import React, { useEffect, useState, useRef } from 'react';
import { Box } from '@mui/material';
import LogLine from './LogLine';

/**
 * Консоль логов (SSE).
 * - Служебные сообщения ([SSE] …) отфильтровываются на клиенте.
 */
export default function LogConsole({ skipCache = false }) {
    const [logs, setLogs]   = useState([]);
    const evtSourceRef      = useRef(null);
    const containerRef      = useRef(null);

    useEffect(() => {
        const baseUrl = process.env.NODE_ENV === 'development'
            ? 'http://localhost:8111/api/v1/logs/stream'
            : '/api/v1/logs/stream';
        const url = skipCache ? `${baseUrl}?skipCache=true` : baseUrl;

        function connect() {
            const source = new EventSource(url);
            evtSourceRef.current = source;

            source.addEventListener('log', e => {
                // отбрасываем всё «внутреннее»
                if (e.data.includes('[SSE]')) return;
                setLogs(prev => [...prev, e.data]);
            });

            source.onerror = err => {
                console.error('SSE connection error (will retry automatically):', err);
            };
        }

        connect();
        return () => evtSourceRef.current?.close();
    }, [skipCache]);

    /* автопрокрутка */
    useEffect(() => {
        containerRef.current?.scrollTo({ top: containerRef.current.scrollHeight, behavior: 'smooth' });
    }, [logs]);

    return (
        <Box
            component="pre"
            ref={containerRef}
            sx={{
                maxHeight: 300,
                height   : 300,
                overflowY: 'auto',
                backgroundColor: 'background.paper',
                p: 1,
                borderRadius: 1,
                fontFamily  : 'monospace',
                whiteSpace  : 'pre-wrap',
                wordBreak   : 'break-word',
            }}
        >
            {logs.map((line, idx) => (
                <LogLine key={idx} raw={line}/>
            ))}
        </Box>
    );
}