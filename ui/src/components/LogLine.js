import React from 'react';
import PropTypes from 'prop-types';
import { Box, Typography, useTheme } from '@mui/material';
import { stripAnsi } from '../utils/helper';

export default function LogLine({ raw }) {
    const theme = useTheme();

    // 1) убираем ANSI-коды и имена потоков
    let line = stripAnsi(raw).replace(/\[[^\]]+\]\s*/g, '').trim();

    // 2) разбираем сначала время и уровень, а всё остальное — в msg
    //    время   уровень   любые символы  «-»  сообщение
    const re = /^(\d{2}:\d{2}:\d{2})\s+(TRACE|DEBUG|INFO|WARN|ERROR)\s+(.+?)\s*-\s*(.*)$/;
    const m  = line.match(re);

    if (!m) {
        // если всё-таки не подошло, просто выводим целиком чёрным
        return <Typography variant="body2">{line}</Typography>;
    }

    const [, time, level, loggerName, msg] = m;

    // цвета по уровням
    const colorMap = {
        TRACE: theme.palette.text.secondary,
        DEBUG: theme.palette.text.secondary,
        INFO:  theme.palette.success.main,
        WARN:  theme.palette.warning.main,
        ERROR: theme.palette.error.main,
    };

    return (
        <Box display="flex" alignItems="baseline">
            {/* время */}
            <Typography
                variant="body2"
                sx={{ width: 70, color: theme.palette.text.secondary, flexShrink: 0 }}
            >
                {time}
            </Typography>

            {/* уровень */}
            <Typography
                variant="body2"
                sx={{
                    fontWeight: 'bold',
                    color: colorMap[level] || theme.palette.text.primary,
                    width: 50,
                    flexShrink: 0,
                    textAlign: 'center',
                    mr: 1
                }}
            >
                {level}
            </Typography>

            {/* логгер (можно убрать, или оставить чуть посветлее) */}
            <Typography
                variant="body2"
                sx={{ color: theme.palette.text.secondary, mr: 1 }}
            >
                {loggerName}
            </Typography>

            {/* сообщение */}
            <Typography variant="body2" sx={{ color: theme.palette.text.primary }}>
                {msg}
            </Typography>
        </Box>
    );
}

LogLine.propTypes = {
    raw: PropTypes.string.isRequired,
};