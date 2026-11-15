        // 导航切换
        document.querySelectorAll('.nav-link').forEach(link => {
            link.addEventListener('click', (e) => {
                e.preventDefault();
                const pageName = link.dataset.page;

                // 更新导航状态
                document.querySelectorAll('.nav-link').forEach(l => l.classList.remove('active'));
                link.classList.add('active');

                // 切换页面
                document.querySelectorAll('.page').forEach(p => p.classList.remove('active'));
                document.getElementById(`${pageName}-page`).classList.add('active');

                // 加载数据
                loadPageData(pageName);
            });
        });

        // 加载页面数据
        function loadPageData(pageName) {
            switch(pageName) {
                case 'overview':
                    loadOverviewData();
                    break;
                case 'documents':
                    loadDocuments();
                    break;
                case 'ingestion-history':
                    loadIngestionStats();
                    loadIngestionHistory(0);
                    break;
                case 'query-history':
                    loadQueryStats();
                    loadQueryHistory(0);
                    break;
            }
        }

        // 加载系统总览数据
        async function loadOverviewData() {
            try {
                const response = await fetch('/api/stats/overview');
                const data = await response.json();

                document.getElementById('total-docs').textContent = data.totalDocuments || 0;
                document.getElementById('total-chunks').textContent = data.totalChunks || 0;
                document.getElementById('total-collections').textContent = data.totalCollections || 0;

                // 加载Collection统计
                const collectionsResponse = await fetch('/api/stats/collections');
                const collections = await collectionsResponse.json();

                const tbody = document.querySelector('#collections-table tbody');
                tbody.innerHTML = '';

                if (collections.length === 0) {
                    tbody.innerHTML = '<tr><td colspan="3" class="empty-state"><div>📊</div><p>暂无Collection</p></td></tr>';
                } else {
                    collections.forEach(c => {
                        tbody.innerHTML += `
                            <tr>
                                <td>${c.collectionName}</td>
                                <td>${c.documentCount}</td>
                                <td>${c.chunkCount}</td>
                            </tr>
                        `;
                    });
                }
            } catch (error) {
                console.error('加载数据失败:', error);
                showNotification('加载数据失败', 'error');
            }
        }

        // 加载文档列表
        async function loadDocuments() {
            try {
                const response = await fetch('/api/documents');
                const data = await response.json();
                const documents = data.documents || [];

                const tbody = document.querySelector('#documents-table tbody');
                tbody.innerHTML = '';

                if (documents.length === 0) {
                    tbody.innerHTML = '<tr><td colspan="5" class="empty-state"><div>📄</div><p>暂无文档</p></td></tr>';
                } else {
                    documents.forEach(doc => {
                        tbody.innerHTML += `
                            <tr>
                                <td>${doc.source || doc.id}</td>
                                <td>${doc.collection || '-'}</td>
                                <td>${doc.chunkCount || '-'}</td>
                                <td>${doc.createdAt ? new Date(doc.createdAt).toLocaleString() : '-'}</td>
                                <td>
                                    <button class="btn btn-sm" onclick="viewChunks('${doc.id}', '${escapeHtml(doc.source || doc.id)}')">查看切块</button>
                                    <button class="btn btn-sm btn-danger" onclick="deleteDocument('${doc.id}')">删除</button>
                                </td>
                            </tr>
                        `;
                    });
                }
            } catch (error) {
                console.error('加载文档失败:', error);
            }
        }

        // 查看文档chunks
        async function viewChunks(docId, docName) {
            const modal = document.getElementById('chunks-modal');
            const title = document.getElementById('chunks-modal-title');
            const body = document.getElementById('chunks-modal-body');

            title.textContent = `切块内容 - ${docName}`;
            body.innerHTML = '<div class="loading"><div class="spinner"></div>加载中...</div>';
            modal.classList.add('active');

            try {
                const response = await fetch(`/api/documents/${docId}/chunks`);
                const data = await response.json();

                if (data.error) {
                    body.innerHTML = `
                        <div style="padding: 20px; text-align: center; color: #e74c3c;">
                            <p>❌ ${data.error}</p>
                        </div>
                    `;
                    return;
                }

                const chunks = data.chunks || [];

                if (chunks.length === 0) {
                    body.innerHTML = `
                        <div class="empty-state">
                            <div>📦</div>
                            <p>该文档暂无切块数据</p>
                        </div>
                    `;
                } else {
                    body.innerHTML = `
                        <div style="margin-bottom: 16px; color: #666;">
                            共 ${chunks.length} 个切块
                        </div>
                        ${chunks.map((chunk, index) => `
                            <div class="chunk-item">
                                <div class="chunk-header">
                                    <span class="chunk-id">Chunk #${index + 1} - ${chunk.id || 'N/A'}</span>
                                    ${chunk.page ? `<span class="chunk-page">第 ${chunk.page} 页</span>` : ''}
                                </div>
                                <div class="chunk-text">${escapeHtml(chunk.text || '(空内容)')}</div>
                            </div>
                        `).join('')}
                    `;
                }
            } catch (error) {
                console.error('加载chunks失败:', error);
                body.innerHTML = `
                    <div style="padding: 20px; text-align: center; color: #e74c3c;">
                        <p>❌ 加载失败: ${error.message}</p>
                    </div>
                `;
            }
        }

        // 关闭模态框
        function closeChunksModal() {
            const modal = document.getElementById('chunks-modal');
            modal.classList.remove('active');
        }

        // 点击模态框外部关闭
        document.getElementById('chunks-modal').addEventListener('click', (e) => {
            if (e.target.id === 'chunks-modal') {
                closeChunksModal();
            }
        });

        // ESC键关闭模态框
        document.addEventListener('keydown', (e) => {
            if (e.key === 'Escape') {
                closeChunksModal();
            }
        });

        // HTML转义函数
        function escapeHtml(text) {
            const div = document.createElement('div');
            div.textContent = text;
            return div.innerHTML;
        }

        // 文件上传
        const uploadArea = document.getElementById('upload-area');
        const fileInput = document.getElementById('file-input');

        uploadArea.addEventListener('click', () => fileInput.click());

        uploadArea.addEventListener('dragover', (e) => {
            e.preventDefault();
            uploadArea.classList.add('dragging');
        });

        uploadArea.addEventListener('dragleave', () => {
            uploadArea.classList.remove('dragging');
        });

        uploadArea.addEventListener('drop', (e) => {
            e.preventDefault();
            uploadArea.classList.remove('dragging');
            const files = e.dataTransfer.files;
            handleFiles(files);
        });

        fileInput.addEventListener('change', (e) => {
            handleFiles(e.target.files);
        });

        // 处理上传文件
        async function handleFiles(files) {
            if (files.length === 0) return;

            const collection = document.getElementById('collection-input').value;
            document.getElementById('upload-progress').style.display = 'block';
            document.getElementById('upload-results').innerHTML = '';

            for (let i = 0; i < files.length; i++) {
                const file = files[i];
                await uploadFile(file, collection, i + 1, files.length);
            }
        }

        // 上传单个文件
        async function uploadFile(file, collection, index, total) {
            const formData = new FormData();
            formData.append('file', file);
            if (collection) formData.append('collection', collection);

            document.getElementById('upload-status').textContent = `上传中: ${file.name} (${index}/${total})`;

            try {
                const response = await fetch('/api/ingest', {
                    method: 'POST',
                    body: formData
                });

                const result = await response.json();

                // 检查是否是重复文件错误（使用 errorCode，更可靠）
                if (result?.status === 'FAILED' && result?.errorCode === 'DUPLICATE_FILE') {
                    // 重复文件：显示黄色警告
                    document.getElementById('upload-results').innerHTML += `
                        <div style="padding: 10px; margin-top: 10px; background: #fff3cd; border-radius: 4px; color: #856404; border-left: 4px solid #ffc107;">
                            ⚠ ${file.name}: 文件已存在，与已上传的文件内容相同，跳过
                        </div>
                    `;
                } else if (result?.status === 'SUCCESS' && result?.chunkCount > 0) {
                    // 成功上传：显示绿色成功消息和耗时统计
                    // 构建耗时统计显示
                    let timingHtml = '';
                    if (result.timing) {
                        const timing = result.timing;
                        const totalTime = timing.total || 0;

                        // 计算各阶段百分比
                        const percentages = {
                            loading: ((timing.loading / totalTime) * 100).toFixed(1),
                            splitting: ((timing.splitting / totalTime) * 100).toFixed(1),
                            refining: ((timing.refining / totalTime) * 100).toFixed(1),
                            enriching: ((timing.enriching / totalTime) * 100).toFixed(1),
                            embedding: ((timing.embedding / totalTime) * 100).toFixed(1),
                            storage: ((timing.storage / totalTime) * 100).toFixed(1)
                        };

                        timingHtml = `
                            <div class="timing-stats">
                                <div class="timing-header">⏱️ 耗时统计：总耗时 ${totalTime}ms (${(totalTime/1000).toFixed(2)}秒)</div>

                                <div class="timing-bar-container">
                                    ${timing.loading > 0 ? `<div class="timing-bar timing-bar-loading" style="width: ${percentages.loading}%;" title="文档加载: ${timing.loading}ms">${percentages.loading >= 5 ? percentages.loading + '%' : ''}</div>` : ''}
                                    ${timing.splitting > 0 ? `<div class="timing-bar timing-bar-splitting" style="width: ${percentages.splitting}%;" title="数据切分: ${timing.splitting}ms">${percentages.splitting >= 5 ? percentages.splitting + '%' : ''}</div>` : ''}
                                    ${timing.refining > 0 ? `<div class="timing-bar timing-bar-refining" style="width: ${percentages.refining}%;" title="降噪处理: ${timing.refining}ms">${percentages.refining >= 5 ? percentages.refining + '%' : ''}</div>` : ''}
                                    ${timing.enriching > 0 ? `<div class="timing-bar timing-bar-enriching" style="width: ${percentages.enriching}%;" title="元数据增强: ${timing.enriching}ms">${percentages.enriching >= 5 ? percentages.enriching + '%' : ''}</div>` : ''}
                                    ${timing.embedding > 0 ? `<div class="timing-bar timing-bar-embedding" style="width: ${percentages.embedding}%;" title="向量化: ${timing.embedding}ms">${percentages.embedding >= 5 ? percentages.embedding + '%' : ''}</div>` : ''}
                                    ${timing.storage > 0 ? `<div class="timing-bar timing-bar-storage" style="width: ${percentages.storage}%;" title="存储入库: ${timing.storage}ms">${percentages.storage >= 5 ? percentages.storage + '%' : ''}</div>` : ''}
                                </div>

                                <div class="timing-details">
                                    <div class="timing-item">
                                        <span class="timing-dot timing-dot-loading"></span>
                                        文档加载: ${timing.loading}ms (${percentages.loading}%)
                                    </div>
                                    <div class="timing-item">
                                        <span class="timing-dot timing-dot-splitting"></span>
                                        数据切分: ${timing.splitting}ms (${percentages.splitting}%)
                                    </div>
                                    <div class="timing-item">
                                        <span class="timing-dot timing-dot-refining"></span>
                                        降噪处理: ${timing.refining}ms (${percentages.refining}%)
                                    </div>
                                    <div class="timing-item">
                                        <span class="timing-dot timing-dot-enriching"></span>
                                        元数据增强: ${timing.enriching}ms (${percentages.enriching}%)
                                    </div>
                                    <div class="timing-item">
                                        <span class="timing-dot timing-dot-embedding"></span>
                                        向量化: ${timing.embedding}ms (${percentages.embedding}%)
                                    </div>
                                    <div class="timing-item">
                                        <span class="timing-dot timing-dot-storage"></span>
                                        存储入库: ${timing.storage}ms (${percentages.storage}%)
                                    </div>
                                </div>
                            </div>
                        `;
                    }

                    document.getElementById('upload-results').innerHTML += `
                        <div style="padding: 10px; margin-top: 10px; background: #d4edda; border-radius: 4px; color: #155724;">
                            ✓ ${file.name}: 成功摄取 ${result.chunkCount || 0} 个chunks
                            ${timingHtml}
                        </div>
                    `;
                } else {
                    // 其他失败情况：显示红色错误消息
                    const errorMsg = result.message || '未知错误';
                    document.getElementById('upload-results').innerHTML += `
                        <div style="padding: 10px; margin-top: 10px; background: #f8d7da; border-radius: 4px; color: #721c24;">
                            ✗ ${file.name}: 摄取失败 - ${errorMsg}
                        </div>
                    `;
                }

                const progress = (index / total) * 100;
                document.getElementById('progress-bar').style.width = progress + '%';
                document.getElementById('progress-bar').textContent = Math.round(progress) + '%';

            } catch (error) {
                document.getElementById('upload-results').innerHTML += `
                    <div style="padding: 10px; margin-top: 10px; background: #f8d7da; border-radius: 4px; color: #721c24;">
                        ✗ ${file.name}: 上传失败 - ${error.message}
                    </div>
                `;
            }
        }

        // 执行查询
        async function executeQuery() {
            const query = document.getElementById('query-input').value;
            if (!query.trim()) {
                alert('请输入查询问题');
                return;
            }

            const topK = parseInt(document.getElementById('topk-input').value);
            const enableRerank = document.getElementById('rerank-input').checked;
            const enableStream = document.getElementById('stream-input').checked;

            document.getElementById('query-results').style.display = 'block';
            document.getElementById('query-answer').innerHTML = '<div class="loading"><div class="spinner"></div>查询中...</div>';
            document.getElementById('query-sources').innerHTML = '';

            if (enableStream) {
                // 流式 RAG 模式：检索 + LLM 生成
                executeStreamQuery(query, topK, enableRerank);
            } else {
                // 纯检索模式：只显示检索文档
                executeRetrieveOnly(query, topK, enableRerank);
            }
        }

        // 纯检索模式（不生成答案）
        async function executeRetrieveOnly(query, topK, enableRerank) {
            try {
                const response = await fetch('/api/retrieve', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ question: query, topK, enableRerank })
                });

                const result = await response.json();

                // 调试日志
                console.log('Retrieve result:', result);
                console.log('Sources:', result.sources);

                // 清空答案区域（不显示提示）
                document.getElementById('query-answer').innerHTML = '';

                // 显示检索文档
                let sourcesHtml = '';
                if (result.sources && result.sources.length > 0) {
                    // 构建耗时数据（纯检索模式）
                    const timingData = {
                        denseSearch: result.timing?.retrieval?.denseSearch || 0,
                        sparseSearch: result.timing?.retrieval?.sparseSearch || 0,
                        rrfFusion: result.timing?.retrieval?.rrfFusion || 0,
                        reranker: result.timing?.retrieval?.reranker || 0,
                        total: result.timing?.retrieval?.total || result.timing?.total || 0
                    };

                    // 先显示耗时统计
                    sourcesHtml = generateTimingStatsHtml(timingData);

                    // 再显示文档列表
                    result.sources.forEach((src, index) => {
                        const metadata = src.metadata || {};
                        const fileName = metadata.file_name || metadata.source || '未知来源';
                        const page = metadata.page ? `第${metadata.page}页` : '';

                        sourcesHtml += `
                            <div style="background: white; padding: 16px; margin-bottom: 12px; border-radius: 8px; border: 1px solid #e0e0e0; box-shadow: 0 1px 3px rgba(0,0,0,0.05);">
                                <div style="font-weight: 600; color: #1976d2; margin-bottom: 8px; font-size: 15px;">
                                    [${index + 1}] ${escapeHtml(src.id)}
                                </div>
                                <div style="color: #666; font-size: 13px; margin-bottom: 8px;">
                                    📄 来源: ${escapeHtml(fileName)} ${page}
                                </div>
                                <div style="color: #333; font-size: 14px; line-height: 1.6; background: #f8f9fa; padding: 12px; border-radius: 4px;">
                                    ${escapeHtml(src.text.substring(0, 400))}${src.text.length > 400 ? '...' : ''}
                                </div>
                            </div>
                        `;
                    });
                } else {
                    sourcesHtml = '<div style="color: #999; padding: 20px; text-align: center;">未检索到相关文档</div>';
                }

                document.getElementById('query-sources').innerHTML = sourcesHtml;

            } catch (error) {
                console.error('检索失败:', error);
                document.getElementById('query-answer').innerHTML = `
                    <div style="padding: 16px; background: #f8d7da; border-radius: 4px; color: #721c24;">
                        检索失败: ${escapeHtml(error.message)}
                    </div>
                `;
                document.getElementById('query-sources').innerHTML = '';
            }
        }

        // 普通查询
        async function executeNormalQuery(query, topK, enableRerank) {
            try {
                const response = await fetch('/api/query', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ question: query, topK, enableRerank })
                });

                const result = await response.json();

                // 调试：查看返回的数据结构
                console.log('Query result:', result);
                console.log('Sources:', result.sources);
                console.log('Sources length:', result.sources?.length);

                // 使用 markdown-it 渲染 Markdown
                let renderedAnswer = result.answer ? md.render(result.answer) : '无回答';

                // 构建答案显示
                let answerHtml = `
                    <div style="padding: 20px; background: white; border-radius: 8px; line-height: 1.8; font-size: 15px; border: 1px solid #e0e0e0;">
                        <div class="markdown-body">
                            ${renderedAnswer}
                        </div>
                    </div>
                `;

                // 构建耗时统计显示
                if (result.timing) {
                    const timing = result.timing;
                    const total = timing.total || 0;

                    // 检索阶段详细耗时
                    const retrieval = timing.retrieval || {};
                    const retrievalTotal = retrieval.total || 0;

                    const percentages = {
                        retrieval: ((retrievalTotal / total) * 100).toFixed(1),
                        promptBuild: ((timing.promptBuild / total) * 100).toFixed(1),
                        llmGeneration: ((timing.llmGeneration / total) * 100).toFixed(1),
                        citationBuild: ((timing.citationBuild / total) * 100).toFixed(1)
                    };

                    answerHtml += `
                        <div class="timing-stats" style="margin-top: 16px;">
                            <div class="timing-header">⏱️ 查询耗时：总耗时 ${total}ms (${(total/1000).toFixed(2)}秒)</div>

                            <div class="timing-bar-container">
                                ${retrievalTotal > 0 ? `<div class="timing-bar timing-bar-loading" style="width: ${percentages.retrieval}%;" title="检索: ${retrievalTotal}ms">${percentages.retrieval >= 8 ? percentages.retrieval + '%' : ''}</div>` : ''}
                                ${timing.promptBuild > 0 ? `<div class="timing-bar timing-bar-splitting" style="width: ${percentages.promptBuild}%;" title="Prompt构建: ${timing.promptBuild}ms">${percentages.promptBuild >= 8 ? percentages.promptBuild + '%' : ''}</div>` : ''}
                                ${timing.llmGeneration > 0 ? `<div class="timing-bar timing-bar-embedding" style="width: ${percentages.llmGeneration}%;" title="LLM生成: ${timing.llmGeneration}ms">${percentages.llmGeneration >= 8 ? percentages.llmGeneration + '%' : ''}</div>` : ''}
                                ${timing.citationBuild > 0 ? `<div class="timing-bar timing-bar-storage" style="width: ${percentages.citationBuild}%;" title="引用构建: ${timing.citationBuild}ms">${percentages.citationBuild >= 8 ? percentages.citationBuild + '%' : ''}</div>` : ''}
                            </div>

                            <div class="timing-details">
                                <div class="timing-item" style="grid-column: 1 / -1; font-weight: 600; margin-bottom: 8px;">
                                    <span class="timing-dot timing-dot-loading"></span>
                                    检索: ${retrievalTotal}ms (${percentages.retrieval}%)
                                </div>
                                ${retrieval.denseSearch ? `
                                <div class="timing-item" style="padding-left: 20px;">
                                    <span style="color: #3498db;">▸</span>
                                    相似度检索(Dense): ${retrieval.denseSearch}ms
                                </div>` : ''}
                                ${retrieval.sparseSearch ? `
                                <div class="timing-item" style="padding-left: 20px;">
                                    <span style="color: #3498db;">▸</span>
                                    关键字检索(Sparse): ${retrieval.sparseSearch}ms
                                </div>` : ''}
                                ${retrieval.rrfFusion ? `
                                <div class="timing-item" style="padding-left: 20px;">
                                    <span style="color: #3498db;">▸</span>
                                    RRF融合: ${retrieval.rrfFusion}ms
                                </div>` : ''}
                                ${retrieval.reranker ? `
                                <div class="timing-item" style="padding-left: 20px;">
                                    <span style="color: #3498db;">▸</span>
                                    Reranker精排: ${retrieval.reranker}ms
                                </div>` : ''}
                                <div class="timing-item">
                                    <span class="timing-dot timing-dot-splitting"></span>
                                    Prompt构建: ${timing.promptBuild}ms (${percentages.promptBuild}%)
                                </div>
                                <div class="timing-item">
                                    <span class="timing-dot timing-dot-embedding"></span>
                                    LLM生成: ${timing.llmGeneration}ms (${percentages.llmGeneration}%)
                                </div>
                                <div class="timing-item">
                                    <span class="timing-dot timing-dot-storage"></span>
                                    引用构建: ${timing.citationBuild}ms (${percentages.citationBuild}%)
                                </div>
                            </div>
                        </div>
                    `;
                }

                document.getElementById('query-answer').innerHTML = answerHtml;

                // 显示检索到的文档
                if (result.sources && result.sources.length > 0) {
                    document.getElementById('query-sources').innerHTML = `
                        <div style="margin-bottom: 12px; font-weight: 600;">检索到 ${result.sources.length} 个相关文档：</div>
                        ${result.sources.map((src, i) => `
                            <div style="padding: 12px; margin-top: 8px; background: #f8f9fa; border-radius: 4px; border-left: 3px solid #3498db;">
                                <div style="display: flex; justify-content: space-between; margin-bottom: 8px;">
                                    <strong style="color: #2c3e50;">[${i + 1}] ${src.id || 'Document'}</strong>
                                    ${src.metadata && src.metadata.source ? `<span style="font-size: 12px; color: #666;">来源: ${src.metadata.source}</span>` : ''}
                                </div>
                                <div style="color: #555; line-height: 1.5; max-height: 120px; overflow: hidden; text-overflow: ellipsis;">
                                    ${escapeHtml(src.text || '').substring(0, 300)}${src.text && src.text.length > 300 ? '...' : ''}
                                </div>
                            </div>
                        `).join('')}
                    `;
                } else {
                    document.getElementById('query-sources').innerHTML = '<div style="color: #999;">未检索到相关文档</div>';
                }

            } catch (error) {
                document.getElementById('query-answer').innerHTML = `
                    <div style="padding: 16px; background: #f8d7da; border-radius: 4px; color: #721c24;">
                        查询失败: ${error.message}
                    </div>
                `;
            }
        }

        // 删除文档
        async function deleteDocument(id) {
            if (!confirm('确定要删除这个文档吗？')) return;

            try {
                await fetch(`/api/documents/${id}`, { method: 'DELETE' });
                loadDocuments();
                showNotification('文档已删除', 'success');
            } catch (error) {
                showNotification('删除失败', 'error');
            }
        }

        // 通知提示
        function showNotification(message, type) {
            // 简单实现，可以后续使用更好的通知库
            alert(message);
        }

        // 流式查询
        async function executeStreamQuery(query, topK, enableRerank) {
            try {
                const streamStartTime = Date.now();

                // 显示加载状态
                document.getElementById('query-answer').innerHTML = `
                    <div style="padding: 16px; background: #f8f9fa; border-radius: 4px; line-height: 1.8;">
                        <div style="color: #666; font-size: 14px; margin-bottom: 8px;">⚡ 正在实时生成答案...</div>
                        <div id="stream-answer" class="markdown-body" style="min-height: 40px;"></div>
                    </div>
                `;

                // 清空 sources 区域
                document.getElementById('query-sources').innerHTML = '';

                // 流式生成答案
                const url = `/api/query/stream?question=${encodeURIComponent(query)}&topK=${topK}&enableRerank=${enableRerank}`;
                const response = await fetch(url);

                if (!response.ok) {
                    throw new Error('流式查询失败');
                }

                const reader = response.body.getReader();
                const decoder = new TextDecoder();
                let accumulatedText = '';
                let timingData = null;

                while (true) {
                    const { done, value } = await reader.read();

                    if (done) {
                        break;
                    }

                    const chunk = decoder.decode(value, { stream: true });

                    // 解析SSE格式：移除 "data:" 前缀
                    const lines = chunk.split('\n');
                    for (const line of lines) {
                        if (line.startsWith('data:')) {
                            const content = line.substring(5).trim(); // 移除 "data:" 并去掉空格

                            if (!content) continue;

                            // 检查是否包含耗时数据标记
                            if (content.includes('__TIMING_DATA__')) {
                                try {
                                    // 提取JSON部分（移除标记前缀）
                                    const jsonStr = content.replace('__TIMING_DATA__', '');
                                    timingData = JSON.parse(jsonStr);
                                    console.log('收到耗时统计:', timingData);
                                } catch (e) {
                                    console.error('解析耗时数据失败:', e, content);
                                }
                            } else {
                                // 普通文本内容
                                accumulatedText += content;
                                // 每次接收到新内容都立即渲染，实现逐字显示
                                if (document.getElementById('stream-answer')) {
                                    document.getElementById('stream-answer').innerHTML = formatAnswer(accumulatedText);
                                }
                            }
                        }
                    }
                }

                // 流式完成，显示最终答案和耗时统计
                if (timingData) {
                    // 使用后端返回的详细耗时数据
                    document.getElementById('query-answer').innerHTML =
                        generateTimingStatsHtml(timingData) +
                        `<div style="padding: 16px; background: #f8f9fa; border-radius: 4px; line-height: 1.8; margin-top: 12px;">
                            <div class="markdown-body">
                                ${formatAnswer(accumulatedText)}
                            </div>
                        </div>`;
                } else {
                    // 降级处理：如果没有收到耗时数据，只显示答案
                    console.warn('未收到耗时统计数据');
                    document.getElementById('query-answer').innerHTML = `
                        <div style="padding: 16px; background: #f8f9fa; border-radius: 4px; line-height: 1.8;">
                            <div class="markdown-body">
                                ${formatAnswer(accumulatedText)}
                            </div>
                        </div>`;
                }

            } catch (error) {
                document.getElementById('query-answer').innerHTML = `
                    <div style="padding: 16px; background: #f8d7da; border-radius: 4px; color: #721c24;">
                        查询失败: ${error.message}
                    </div>
                `;
            }
        }

        // 初始化 markdown-it（配置更宽容的选项）
        const md = window.markdownit({
            html: true,        // 允许 HTML 标签
            breaks: true,      // 将换行符转换为 <br>
            linkify: true,     // 自动识别链接
            typographer: true  // 启用智能标点替换
        });

        // 生成耗时统计HTML（可视化进度条）
        function generateTimingStatsHtml(timingData) {
            const totalTime = timingData.total;

            // 计算各阶段占比
            const percentages = {};
            let accountedTime = 0;
            for (let key in timingData) {
                if (key !== 'total') {
                    percentages[key] = totalTime > 0 ? ((timingData[key] / totalTime) * 100).toFixed(1) : 0;
                    accountedTime += timingData[key];
                }
            }

            // 计算未统计的时间（其他开销）
            const otherTime = totalTime - accountedTime;
            const otherPercentage = totalTime > 0 ? ((otherTime / totalTime) * 100).toFixed(1) : 0;

            // 阶段配置（定义显示顺序、标签、颜色）
            const stageConfigs = {
                denseSearch: { label: 'Dense检索', color: 'loading', order: 1 },
                sparseSearch: { label: 'Sparse检索', color: 'splitting', order: 2 },
                rrfFusion: { label: 'RRF融合', color: 'refining', order: 3 },
                reranker: { label: 'Rerank', color: 'enriching', order: 4 },
                retrieval: { label: '检索总耗时', color: 'loading', order: 5 },
                promptBuild: { label: 'Prompt构建', color: 'splitting', order: 6 },
                ttft: { label: 'LLM首字延迟', color: 'enriching', order: 7 },
                streamGeneration: { label: '流式生成', color: 'embedding', order: 8 },
                generation: { label: '生成', color: 'embedding', order: 9 }
            };

            // 按顺序构建进度条HTML（只显示有耗时的阶段，跳过retrieval避免重复）
            let progressBarHtml = '';
            const sortedKeys = Object.keys(timingData)
                .filter(key => key !== 'total' && key !== 'retrieval' && timingData[key] > 0)
                .sort((a, b) => (stageConfigs[a]?.order || 99) - (stageConfigs[b]?.order || 99));

            for (let key of sortedKeys) {
                const config = stageConfigs[key] || { label: key, color: 'storage' };
                const percentage = percentages[key];
                progressBarHtml += `<div class="timing-bar timing-bar-${config.color}" style="width: ${percentage}%;" title="${config.label}: ${timingData[key]}ms">${percentage >= 5 ? percentage + '%' : ''}</div>`;
            }

            // 添加"其他"项（如果有未统计的时间且大于1ms，避免舍入误差）
            if (otherTime > 1) {
                progressBarHtml += `<div class="timing-bar timing-bar-storage" style="width: ${otherPercentage}%;" title="其他: ${otherTime}ms">${otherPercentage >= 5 ? otherPercentage + '%' : ''}</div>`;
            }

            // 构建详细列表HTML（分组显示：检索阶段 + 生成阶段）
            let detailsHtml = '';

            // 检索阶段（如果有子阶段，显示折叠的层级结构）
            const hasRetrievalDetails = timingData.denseSearch || timingData.sparseSearch ||
                                       timingData.rrfFusion || timingData.reranker;

            if (hasRetrievalDetails) {
                // 有详细检索数据，显示总览和子项
                const retrievalTotal = timingData.retrieval ||
                    (timingData.denseSearch + timingData.sparseSearch + timingData.rrfFusion + timingData.reranker);
                const retrievalPercentage = totalTime > 0 ? ((retrievalTotal / totalTime) * 100).toFixed(1) : 0;

                detailsHtml += `
                    <div class="timing-item" style="font-weight: 600; grid-column: 1 / -1;">
                        <span class="timing-dot timing-dot-loading"></span>
                        检索: ${retrievalTotal}ms (${retrievalPercentage}%)
                    </div>`;

                // 子项（缩进显示）
                ['denseSearch', 'sparseSearch', 'rrfFusion', 'reranker'].forEach(key => {
                    if (timingData[key] !== undefined) {
                        const config = stageConfigs[key];
                        detailsHtml += `
                            <div class="timing-item" style="padding-left: 20px; color: #666;">
                                <span style="color: #3498db;">▸</span>
                                ${config.label}: ${timingData[key]}ms (${percentages[key]}%)
                            </div>`;
                    }
                });
            } else if (timingData.retrieval) {
                // 只有检索总耗时，没有详细数据
                detailsHtml += `
                    <div class="timing-item">
                        <span class="timing-dot timing-dot-loading"></span>
                        ${stageConfigs.retrieval.label}: ${timingData.retrieval}ms (${percentages.retrieval}%)
                    </div>`;
            }

            // Prompt构建
            if (timingData.promptBuild !== undefined) {
                detailsHtml += `
                    <div class="timing-item">
                        <span class="timing-dot timing-dot-splitting"></span>
                        ${stageConfigs.promptBuild.label}: ${timingData.promptBuild}ms (${percentages.promptBuild}%)
                    </div>`;
            }

            // LLM生成阶段（如果有TTFT和streamGeneration，显示分解；否则显示generation）
            if (timingData.ttft !== undefined || timingData.streamGeneration !== undefined) {
                // 显示首字延迟
                if (timingData.ttft !== undefined) {
                    detailsHtml += `
                        <div class="timing-item">
                            <span class="timing-dot timing-dot-enriching"></span>
                            ${stageConfigs.ttft.label}: ${timingData.ttft}ms (${percentages.ttft}%)
                        </div>`;
                }
                // 显示流式生成
                if (timingData.streamGeneration !== undefined) {
                    detailsHtml += `
                        <div class="timing-item">
                            <span class="timing-dot timing-dot-embedding"></span>
                            ${stageConfigs.streamGeneration.label}: ${timingData.streamGeneration}ms (${percentages.streamGeneration}%)
                        </div>`;
                }
            } else if (timingData.generation !== undefined) {
                // 只有总生成耗时
                detailsHtml += `
                    <div class="timing-item">
                        <span class="timing-dot timing-dot-embedding"></span>
                        ${stageConfigs.generation.label}: ${timingData.generation}ms (${percentages.generation}%)
                    </div>`;
            }

            // 添加"其他"项到详细列表（如果有未统计的时间且大于1ms）
            if (otherTime > 1) {
                detailsHtml += `
                    <div class="timing-item">
                        <span class="timing-dot timing-dot-storage"></span>
                        其他: ${otherTime}ms (${otherPercentage}%)
                    </div>
                `;
            }

            return `
                <div class="timing-stats">
                    <div class="timing-header">⏱️ 耗时统计：总耗时 ${totalTime}ms (${(totalTime/1000).toFixed(2)}秒)</div>
                    <div class="timing-bar-container">
                        ${progressBarHtml}
                    </div>
                    <div class="timing-details">
                        ${detailsHtml}
                    </div>
                </div>
            `;
        }

        // 格式化答案（使用 markdown-it 渲染 Markdown）
        function formatAnswer(text) {
            if (!text) return '';

            // 预处理：修复一些常见的非标准格式
            let preprocessed = text
                // 移除"引用来源"部分（从"引用来源"到末尾）
                .replace(/引用来源[:：][\s\S]*$/i, '')
                // 修复 "数字.内容"（缺少空格）→ "数字. 内容"
                .replace(/(\d+)\.([^\s])/g, '$1. $2')
                // 强制在列表项前添加换行（关键修复）
                // 匹配：句号/分号 + 空格 + 数字. → 换行 + 数字.
                .replace(/([。；;.!！])\s*(\d+)\.\s+/g, '$1\n\n$2. ')
                // 修复 "数字.:" 或 "数字:" 这样的格式
                .replace(/(\d+)\.?:\s*/g, '\n$1. ')
                // 修复同一行内的多个列表项 "xxx；2. yyy"
                .replace(/；\s*(\d+)\.\s*/g, '\n$1. ')
                // 修复 "###数字." 格式（LLM 误用标题）
                .replace(/###\s*(\d+)\.\s*/g, '\n$1. ')
                // 清理多余的空行
                .replace(/\n{3,}/g, '\n\n')
                // 清理末尾空白
                .trim();

            // 使用 markdown-it 渲染 Markdown
            try {
                return md.render(preprocessed);
            } catch (e) {
                console.error('Markdown 解析失败:', e);
                // 降级处理：简单的文本格式化
                let formatted = escapeHtml(preprocessed);
                formatted = formatted.replace(/\n/g, '<br>');
                formatted = formatted.replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>');
                return formatted;
            }
        }

        // 历史记录相关变量
        let currentIngestionPage = 0;
        let currentQueryPage = 0;

        // 加载历史记录统计
        // 加载文档上传统计
        async function loadIngestionStats() {
            try {
                const response = await fetch('/api/ingestion-history/stats');
                const data = await response.json();

                document.getElementById('ingestion-total').textContent = data.total || 0;
                document.getElementById('ingestion-success').textContent = data.success || 0;
                document.getElementById('ingestion-failed').textContent = data.failed || 0;
                document.getElementById('ingestion-processing').textContent = data.processing || 0;
            } catch (error) {
                console.error('加载文档上传统计失败', error);
            }
        }

        // 加载查询统计
        async function loadQueryStats() {
            try {
                const response = await fetch('/api/history/stats');
                const data = await response.json();

                document.getElementById('query-total').textContent = data.totalQueries || 0;
                document.getElementById('query-success').textContent = data.successQueries || 0;
                document.getElementById('query-failed').textContent = data.failedQueries || 0;

                // 计算平均耗时
                if (data.averageResponseTime) {
                    document.getElementById('query-avg-time').textContent = Math.round(data.averageResponseTime) + 'ms';
                } else {
                    document.getElementById('query-avg-time').textContent = '-';
                }
            } catch (error) {
                console.error('加载查询统计失败', error);
            }
        }

        // 加载文档上传历史
        async function loadIngestionHistory(page) {
            try {
                const response = await fetch(`/api/ingestion-history?page=${page}&size=10`);

                if (!response.ok) {
                    throw new Error(`HTTP ${response.status}: ${response.statusText}`);
                }

                const data = await response.json();

                // 检查是否有错误
                if (data.error) {
                    throw new Error(data.error);
                }

                // 检查数据结构
                if (!data.content) {
                    console.error('API返回数据:', data);
                    throw new Error('API返回数据格式错误：缺少content字段');
                }

                currentIngestionPage = data.currentPage || 0;

                const tbody = document.getElementById('ingestion-history-tbody');
                if (data.content.length === 0) {
                    tbody.innerHTML = '<tr><td colspan="7" class="empty-state"><div>📋</div><p>暂无上传历史</p></td></tr>';
                } else {
                    tbody.innerHTML = data.content.map(item => `
                        <tr>
                            <td style="max-width: 200px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;" title="${escapeHtml(item.filePath || '-')}">${escapeHtml(item.filePath ? item.filePath.split(/[/\\]/).pop() : '-')}</td>
                            <td>${escapeHtml(item.collectionName || 'default')}</td>
                            <td><span class="badge badge-${item.status === 'SUCCESS' ? 'success' : item.status === 'FAILED' ? 'danger' : 'warning'}">${item.status}</span></td>
                            <td>${item.chunkCount || 0}</td>
                            <td>${item.processingTimeMs ? (item.processingTimeMs / 1000).toFixed(2) + 's' : '-'}</td>
                            <td>${item.processedAt ? new Date(item.processedAt).toLocaleString('zh-CN') : '-'}</td>
                            <td style="max-width: 200px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;" title="${escapeHtml(item.errorMsg || '')}">${escapeHtml(item.errorMsg || '-')}</td>
                        </tr>
                    `).join('');
                }

                // 更新分页按钮
                document.getElementById('ingestion-prev-btn').disabled = data.currentPage === 0;
                document.getElementById('ingestion-next-btn').disabled = data.currentPage >= data.totalPages - 1 || data.totalPages === 0;
                document.getElementById('ingestion-page-info').textContent = `第 ${data.currentPage + 1} / ${data.totalPages || 1} 页 (共 ${data.totalElements || 0} 条)`;
            } catch (error) {
                console.error('加载文档上传历史失败', error);
                document.getElementById('ingestion-history-tbody').innerHTML = '<tr><td colspan="7" class="empty-state"><div>❌</div><p>加载失败: ' + escapeHtml(error.message) + '</p></td></tr>';
            }
        }

        // 加载查询历史
        async function loadQueryHistory(page) {
            try {
                const response = await fetch(`/api/history/page?page=${page}&size=10`);

                if (!response.ok) {
                    throw new Error(`HTTP ${response.status}: ${response.statusText}`);
                }

                const data = await response.json();

                // 检查是否有错误
                if (data.error) {
                    throw new Error(data.error);
                }

                // 检查数据结构
                if (!data.content) {
                    console.error('API返回数据:', data);
                    throw new Error('API返回数据格式错误：缺少content字段');
                }

                currentQueryPage = data.currentPage || 0;

                const tbody = document.getElementById('query-history-tbody');
                if (data.content.length === 0) {
                    tbody.innerHTML = '<tr><td colspan="7" class="empty-state"><div>📜</div><p>暂无查询历史</p></td></tr>';
                } else {
                    tbody.innerHTML = data.content.map(item => `
                        <tr>
                            <td style="max-width: 250px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;" title="${escapeHtml(item.question)}">${escapeHtml(item.question || '-')}</td>
                            <td style="max-width: 300px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;" title="${escapeHtml(item.answer || '')}">${escapeHtml(item.answer ? (item.answer.substring(0, 80) + (item.answer.length > 80 ? '...' : '')) : '-')}</td>
                            <td><span class="badge badge-${item.status === 'SUCCESS' ? 'success' : 'danger'}">${item.status}</span></td>
                            <td>${item.responseTimeMs ? (item.responseTimeMs / 1000).toFixed(2) + 's' : '-'}</td>
                            <td>${item.topK || '-'}</td>
                            <td>${item.enableRerank ? '✓' : '✗'}</td>
                            <td style="white-space: nowrap;">${new Date(item.createdAt).toLocaleString('zh-CN')}</td>
                        </tr>
                    `).join('');
                }

                // 更新分页按钮
                document.getElementById('query-prev-btn').disabled = data.currentPage === 0;
                document.getElementById('query-next-btn').disabled = data.currentPage >= data.totalPages - 1 || data.totalPages === 0;
                document.getElementById('query-page-info').textContent = `第 ${data.currentPage + 1} / ${data.totalPages || 1} 页 (共 ${data.totalElements || 0} 条)`;
            } catch (error) {
                console.error('加载查询历史失败', error);
                document.getElementById('query-history-tbody').innerHTML = '<tr><td colspan="7" class="empty-state"><div>❌</div><p>加载失败: ' + escapeHtml(error.message) + '</p></td></tr>';
            }
        }

        // 初始加载
        loadOverviewData();
