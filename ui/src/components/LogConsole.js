import React, { useEffect, useState, useRef } from 'react';
import { Box } from '@mui/material';
import LogLine from './LogLine';

/**
 * LogConsole: подписывается на SSE /api/v1/logs/stream?skipCache={skipCache},
 * отображает только новые события (если skipCache=true),
 * автопрокручивает и переподключается при обрыве.
 *
 * @param {boolean} skipCache — если true (дефолт), не запрашиваем буфер старых логов.
 */
export default function LogConsole({ skipCache = true }) {
    const [logs, setLogs] = useState([]);
    const evtSourceRef = useRef(null);
    const containerRef = useRef(null);

    useEffect(() => {
        const baseUrl =
            process.env.NODE_ENV === 'development'
                ? 'http://localhost:8111/api/v1/logs/stream'
                : '/api/v1/logs/stream';
        const url = skipCache
            ? `${baseUrl}?skipCache=true`
            : baseUrl;

        function connect() {
            const source = new EventSource(url);
            evtSourceRef.current = source;

            // Слушаем только кастомные 'log' события
            source.addEventListener('log', e => {
                setLogs(prev => [...prev, e.data]);
            });

            source.onerror = err => {
                console.error('SSE connection error:', err);
                source.close();
                // переподключаемся через 3 сек
                setTimeout(connect, 3000);
            };
        }

        connect();
        return () => {
            if (evtSourceRef.current) {
                evtSourceRef.current.close();
            }
        };
    }, [skipCache]);

    // автопрокрутка вниз при каждом новом сообщении
    useEffect(() => {
        const el = containerRef.current;
        if (el) {
            el.scrollTo({ top: el.scrollHeight, behavior: 'smooth' });
        }
    }, [logs]);

    return (
        <Box
            component="pre"
            ref={containerRef}
            sx={{
                maxHeight: 300,
                height: 300,
                overflowY: 'auto',
                backgroundColor: 'background.paper',
                p: 1,
                borderRadius: 1,
                fontFamily: 'monospace',
                whiteSpace: 'pre-wrap',
                wordBreak: 'break-word',
            }}
        >
            {logs.map((line, idx) => (
                <LogLine key={idx} raw={line} />
            ))}
        </Box>
    );
}