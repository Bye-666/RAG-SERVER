package com.ragserver.ingestion.loader;

import com.ragserver.entity.IngestionHistory;
import com.ragserver.repository.IngestionHistoryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;

/**
 * 文件完整性服务
 *
 * <p>基于SHA-256哈希实现文件去重和完整性验证。</p>
 *
 * <h3>核心功能</h3>
 * <ul>
 *   <li>计算文件SHA-256哈希值</li>
 *   <li>检查文件是否已成功摄取（去重）</li>
 *   <li>检测文件是否被修改（完整性验证）</li>
 * </ul>
 *
 * <h3>业务场景</h3>
 * <ol>
 *   <li><b>增量摄取</b>：重复文件跳过，避免浪费资源</li>
 *   <li><b>断点续传</b>：失败的文件可以重新摄取</li>
 *   <li><b>内容更新检测</b>：文件修改后重新摄取</li>
 * </ol>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * @Autowired
 * private FileIntegrityService integrityService;
 *
 * // 检查文件是否需要摄取
 * Path file = Path.of("/docs/report.pdf");
 * if (integrityService.shouldSkip(file)) {
 *     log.info("文件已摄取，跳过: {}", file);
 *     return;
 * }
 *
 * // 计算文件哈希
 * String hash = integrityService.computeFileHash(file);
 *
 * // 检查文件是否被修改
 * String oldHash = "previous_hash";
 * if (!integrityService.verifyFileIntegrity(file, oldHash)) {
 *     log.warn("文件已被修改！");
 * }
 * }</pre>
 *
 * @author RAG-SERVER开发团队
 * @since 1.0.0
 */
@Slf4j
@Service
public class FileIntegrityService {

    private final IngestionHistoryRepository historyRepository;

    /**
     * 构造函数
     */
    public FileIntegrityService(IngestionHistoryRepository historyRepository) {
        this.historyRepository = historyRepository;
        log.info("FileIntegrityService初始化完成");
    }

    /**
     * 判断文件是否应该跳过（已成功摄取）
     *
     * <p>检查逻辑：</p>
     * <ol>
     *   <li>计算文件的SHA-256哈希</li>
     *   <li>查询数据库检查该哈希是否存在</li>
     *   <li>如果存在且状态为SUCCESS，返回true（跳过）</li>
     *   <li>如果不存在或状态为FAILED，返回false（需要摄取）</li>
     * </ol>
     *
     * <p>使用场景：</p>
     * <ul>
     *   <li>批量摄取时过滤重复文件</li>
     *   <li>定时任务增量摄取</li>
     *   <li>用户重复上传检测</li>
     * </ul>
     *
     * @param file 待检查的文件
     * @return true=应该跳过（已摄取），false=需要摄取
     * @throws IOException 文件读取失败
     */
    public boolean shouldSkip(Path file) throws IOException {
        String hash = computeFileHash(file);

        Optional<IngestionHistory> history = historyRepository.findById(hash);

        if (history.isPresent()) {
            IngestionHistory.IngestionStatus status = history.get().getStatus();
            boolean shouldSkip = status == IngestionHistory.IngestionStatus.SUCCESS;

            if (shouldSkip) {
                log.info("文件已成功摄取，跳过: {} (hash={})", file.getFileName(), hash);
            } else {
                log.info("文件摄取状态为{}，将重新摄取: {}", status, file.getFileName());
            }

            return shouldSkip;
        }

        log.debug("文件未摄取过，需要摄取: {}", file.getFileName());
        return false;
    }

    /**
     * 计算文件的SHA-256哈希值
     *
     * <p>SHA-256算法特点：</p>
     * <ul>
     *   <li>输出固定64个十六进制字符（256位）</li>
     *   <li>相同内容必然产生相同哈希</li>
     *   <li>不同内容几乎不可能产生相同哈希（碰撞概率 &lt; 2^-128）</li>
     *   <li>无法从哈希反推原文（单向函数）</li>
     * </ul>
     *
     * <p>性能：</p>
     * <ul>
     *   <li>小文件（&lt;1MB）：通常 &lt; 10ms</li>
     *   <li>中等文件（1-10MB）：通常 &lt; 100ms</li>
     *   <li>大文件（&gt;10MB）：约100MB/秒</li>
     * </ul>
     *
     * @param file 文件路径
     * @return SHA-256哈希值（64个十六进制字符）
     * @throws IOException 文件读取失败
     */
    public String computeFileHash(Path file) throws IOException {
        try {
            long startTime = System.currentTimeMillis();

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] fileBytes = Files.readAllBytes(file);
            byte[] hashBytes = digest.digest(fileBytes);

            String hash = bytesToHex(hashBytes);

            long duration = System.currentTimeMillis() - startTime;
            log.debug("计算文件哈希: {} -> {} (耗时{}ms)",
                file.getFileName(), hash, duration);

            return hash;

        } catch (NoSuchAlgorithmException e) {
            // SHA-256应该总是可用
            throw new RuntimeException("SHA-256算法不可用", e);
        }
    }

    /**
     * 验证文件完整性
     *
     * <p>比较文件当前的哈希值与给定的期望哈希值，判断文件是否被修改。</p>
     *
     * <p>使用场景：</p>
     * <ul>
     *   <li>检测文件是否损坏</li>
     *   <li>检测文件是否被篡改</li>
     *   <li>文件下载后完整性验证</li>
     * </ul>
     *
     * <p>示例：</p>
     * <pre>{@code
     * // 下载文件并验证
     * String expectedHash = "abc123..."; // 服务器提供的哈希
     * Path downloaded = Path.of("/tmp/downloaded.pdf");
     *
     * if (integrityService.verifyFileIntegrity(downloaded, expectedHash)) {
     *     log.info("文件完整，可以使用");
     * } else {
     *     log.error("文件损坏或被篡改，请重新下载！");
     * }
     * }</pre>
     *
     * @param file 待验证的文件
     * @param expectedHash 期望的SHA-256哈希值
     * @return true=完整性验证通过，false=文件已被修改
     * @throws IOException 文件读取失败
     */
    public boolean verifyFileIntegrity(Path file, String expectedHash) throws IOException {
        String actualHash = computeFileHash(file);
        boolean isValid = actualHash.equalsIgnoreCase(expectedHash);

        if (!isValid) {
            log.warn("文件完整性验证失败: {} (期望={}, 实际={})",
                file.getFileName(), expectedHash, actualHash);
        }

        return isValid;
    }

    /**
     * 检查文件是否被修改
     *
     * <p>通过比较数据库中记录的哈希与当前文件哈希，判断文件是否被修改。</p>
     *
     * <p>返回值：</p>
     * <ul>
     *   <li>true：文件已被修改（需要重新摄取）</li>
     *   <li>false：文件未修改或从未摄取过</li>
     * </ul>
     *
     * @param file 待检查的文件
     * @return true=文件已被修改，false=文件未修改或未摄取过
     * @throws IOException 文件读取失败
     */
    public boolean isFileModified(Path file) throws IOException {
        String currentHash = computeFileHash(file);

        Optional<IngestionHistory> history = historyRepository.findById(currentHash);

        // 如果数据库中没有此哈希，说明文件是新的或已被修改
        if (history.isEmpty()) {
            // 检查是否有相同路径但不同哈希的记录
            var samePathRecords = historyRepository.findByFilePathContaining(file.getFileName().toString());

            if (!samePathRecords.isEmpty()) {
                log.info("文件已被修改: {} (旧哈希存在，新哈希={})",
                    file.getFileName(), currentHash);
                return true;
            }

            log.debug("文件是新的: {}", file.getFileName());
            return false;
        }

        log.debug("文件未修改: {} (hash={})", file.getFileName(), currentHash);
        return false;
    }

    /**
     * 获取文件的摄取历史
     *
     * <p>根据文件路径查询摄取历史记录。</p>
     *
     * @param file 文件路径
     * @return 摄取历史记录（如果存在）
     * @throws IOException 文件读取失败
     */
    public Optional<IngestionHistory> getIngestionHistory(Path file) throws IOException {
        String hash = computeFileHash(file);
        return historyRepository.findById(hash);
    }

    /**
     * 字节数组转十六进制字符串
     *
     * <p>将SHA-256算法输出的32字节数组转换为64个十六进制字符。</p>
     *
     * @param bytes 字节数组
     * @return 十六进制字符串（小写）
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder(2 * bytes.length);
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }
}
