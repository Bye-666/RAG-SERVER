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
                // 流式查询
                executeStreamQuery(query, topK, enableRerank);
            } else {
                // 普通查询
                executeNormalQuery(query, topK, enableRerank);
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

                // 构建答案显示
                let answerHtml = `
                    <div style="padding: 16px; background: #f8f9fa; border-radius: 4px; line-height: 1.6;">
                        ${result.answer || '无回答'}
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

                // 第一步：先用普通查询获取检索文档
                document.getElementById('query-answer').innerHTML = `
                    <div style="padding: 16px; background: #f8f9fa; border-radius: 4px; line-height: 1.6;">
                        <div style="color: #666; font-size: 14px; margin-bottom: 8px;">🔍 正在检索文档...</div>
                    </div>
                `;

                const retrievalStartTime = Date.now();
                const metaResponse = await fetch('/api/query', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ question: query, topK, enableRerank })
                });

                const metaResult = await metaResponse.json();
                const retrievalDuration = Date.now() - retrievalStartTime;

                // 显示检索到的文档和检索耗时
                let sourcesHtml = '';
                if (metaResult.sources && metaResult.sources.length > 0) {
                    sourcesHtml = `
                        <div style="margin-bottom: 12px; font-weight: 600;">
                            检索到 ${metaResult.sources.length} 个相关文档 (耗时: ${retrievalDuration}ms)
                        </div>
                        ${metaResult.sources.map((src, i) => `
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

                    // 显示检索详细耗时
                    if (metaResult.timing && metaResult.timing.retrieval) {
                        const retrieval = metaResult.timing.retrieval;
                        sourcesHtml += `
                            <div style="margin-top: 12px; padding: 8px; background: rgba(52, 152, 219, 0.1); border-radius: 4px; font-size: 12px;">
                                <strong>检索详情：</strong>
                                Dense: ${retrieval.denseSearch}ms |
                                Sparse: ${retrieval.sparseSearch}ms |
                                RRF: ${retrieval.rrfFusion}ms |
                                Rerank: ${retrieval.reranker}ms |
                                总计: ${retrieval.total}ms
                            </div>
                        `;
                    }
                } else {
                    sourcesHtml = '<div style="color: #999;">未检索到相关文档</div>';
                }
                document.getElementById('query-sources').innerHTML = sourcesHtml;

                // 第二步：流式生成答案
                const generationStartTime = Date.now();
                document.getElementById('query-answer').innerHTML = `
                    <div style="padding: 16px; background: #f8f9fa; border-radius: 4px; line-height: 1.8;">
                        <div style="color: #666; font-size: 14px; margin-bottom: 8px;">⚡ 正在实时生成答案...</div>
                        <div id="stream-answer" style="min-height: 40px;"></div>
                    </div>
                `;

                const url = `/api/query/stream?question=${encodeURIComponent(query)}&topK=${topK}&enableRerank=${enableRerank}`;
                const response = await fetch(url);

                if (!response.ok) {
                    throw new Error('流式查询失败');
                }

                const reader = response.body.getReader();
                const decoder = new TextDecoder();
                let accumulatedText = '';

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
                            const content = line.substring(5); // 移除 "data:"
                            if (content.trim()) {
                                accumulatedText += content;
                                // 每次接收到新内容都立即渲染，实现逐字显示
                                document.getElementById('stream-answer').innerHTML = formatAnswer(accumulatedText);
                            }
                        }
                    }
                }

                // 流式完成，计算总耗时
                const generationDuration = Date.now() - generationStartTime;
                const totalDuration = Date.now() - streamStartTime;

                // 显示最终答案和耗时统计
                document.getElementById('query-answer').innerHTML = `
                    <div style="padding: 16px; background: #f8f9fa; border-radius: 4px; line-height: 1.8;">
                        ${formatAnswer(accumulatedText)}
                    </div>
                    <div style="margin-top: 12px; padding: 8px; background: rgba(46, 204, 113, 0.1); border-radius: 4px; font-size: 12px;">
                        <strong>⏱️ 耗时统计：</strong>
                        检索: ${retrievalDuration}ms |
                        生成: ${generationDuration}ms |
                        总计: ${totalDuration}ms (${(totalDuration/1000).toFixed(2)}秒)
                    </div>
                `;

            } catch (error) {
                document.getElementById('query-answer').innerHTML = `
                    <div style="padding: 16px; background: #f8d7da; border-radius: 4px; color: #721c24;">
                        查询失败: ${error.message}
                    </div>
                `;
            }
        }

        // 格式化答案（支持基本的markdown样式）
        function formatAnswer(text) {
            // 转义HTML
            let formatted = escapeHtml(text);

            // 暂时不处理引用来源，保持完整显示

            // 处理换行
            formatted = formatted.replace(/\n/g, '<br>');

            // 处理加粗 **text**
            formatted = formatted.replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>');

            // 处理列表项
            formatted = formatted.replace(/^(\d+)\.\s/gm, '<br><strong>$1.</strong> ');

            // 处理引用块 > text
            formatted = formatted.replace(/&gt;\s*(.+?)<br>/g, '<blockquote style="border-left: 3px solid #3498db; padding-left: 12px; margin: 8px 0; color: #555;">$1</blockquote>');

            return formatted;
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
                            <td>-</td>
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
