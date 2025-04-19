import React, { useEffect, useState, useRef } from 'react';
import { Box, Typography, useTheme } from '@mui/material';
import LogLine from './LogLine';

export default function LogConsole() {
    const theme = useTheme();
    const [lines, setLines] = useState([]);
    const scrollRef = useRef();
    const lastCountRef = useRef(0);

    useEffect(() => {
        let mounted = true;

        async function fetchLatest() {
            const res = await fetch('/api/v1/logs/latest');
            if (!res.ok) return [];
            return res.json();
        }

        // первый раз — запомним длину
        fetchLatest().then(arr => {
            if (mounted) lastCountRef.current = arr.length;
        });

        // далее каждые 1 с подгружаем только новые строки
        const id = setInterval(async () => {
            const arr = await fetchLatest();
            if (!mounted) return;
            if (arr.length > lastCountRef.current) {
                const newSlice = arr.slice(lastCountRef.current);
                setLines(prev => [...prev, ...newSlice]);
                lastCountRef.current = arr.length;
            }
        }, 1000);

        return () => {
            mounted = false;
            clearInterval(id);
        };
    }, []);

    // автоскролл вниз при новых строках
    useEffect(() => {
        if (scrollRef.current) scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
    }, [lines]);

    return (
        <Box
            ref={scrollRef}
            sx={{
                height: 240,
                overflowY: 'auto',
                whiteSpace: 'pre-wrap',
                wordBreak: 'break-word',
                backgroundColor: theme.palette.background.paper,
                p: 1,
                border: 1,
                borderColor: 'divider',
                borderRadius: 1,
            }}
        >
            {lines.length === 0 ? (
                <Typography variant="body2" color="text.secondary">
                    Ждём новых записей…
                </Typography>
            ) : (
                lines.map((raw, i) => <LogLine key={i} raw={raw} />)
            )}
        </Box>
    );
}