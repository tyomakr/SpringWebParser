import React from 'react';
import PropTypes from 'prop-types';
import { Box, Typography, useTheme } from '@mui/material';
import { stripAnsi } from '../utils/helper';

/**
 * Отрисовка одной строки лога с цветом по LEVEL.
 * Поддерживает 2 формата:
 *   1) HH:mm:ss LEVEL Message
 *   2) HH:mm:ss LEVEL LoggerName - Message
 */
export default function LogLine({ raw }) {
    const theme = useTheme();
    let line = stripAnsi(raw).replace(/\[[^\]]+]/g, '').trim();

    // --- новый короткий шаблон
    let m = line.match(
        /^(\d{2}:\d{2}:\d{2})\s+(TRACE|DEBUG|INFO|WARN|ERROR)\s+(.*)$/
    );

    // --- старый шаблон с LoggerName
    let logger = '';
    if (!m) {
        const old = line.match(
            /^(\d{2}:\d{2}:\d{2})\s+(TRACE|DEBUG|INFO|WARN|ERROR)\s+(.+?)\s*-\s*(.*)$/
        );
        if (old) {
            logger = old[3];
            m = [old[0], old[1], old[2], old[4]];
        }
    }

    // не подошло — выводим как есть
    if (!m) return <Typography component="pre">{line}</Typography>;

    const [, time, level, msg] = m;
    const color = {
        TRACE: theme.palette.text.secondary,
        DEBUG: theme.palette.text.secondary,
        INFO : theme.palette.success.main,
        WARN : theme.palette.warning.main,
        ERROR: theme.palette.error.main,
    }[level] || theme.palette.text.primary;

    return (
        <Box sx={{ display: 'flex' }}>
            <Typography noWrap sx={{ mr: 1, color: theme.palette.text.disabled, flexShrink: 0 }}>
                {time}
            </Typography>
            <Typography noWrap sx={{ mr: 1, color, fontWeight: 600, minWidth: 48, flexShrink: 0 }}>
                {level}
            </Typography>
            {logger && (
                <Typography sx={{ mr: 1, color: theme.palette.text.secondary }}>
                    {logger}
                </Typography>
            )}
            <Typography>{msg}</Typography>
        </Box>
    );
}

LogLine.propTypes = { raw: PropTypes.string.isRequired };