import { createTheme } from '@mui/material/styles';

export default function getTheme(mode) {
    return createTheme({
        palette: {
            mode,                 // 'light' или 'dark'
            primary:   { main: '#1976d2' },
            secondary: { main: '#dc004e' },
        },
    });
}