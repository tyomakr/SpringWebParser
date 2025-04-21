import React from 'react';
import { Link, BrowserRouter, Routes, Route } from 'react-router-dom';
import { inject, observer } from 'mobx-react';
import FWIStepper from './components/FWIStepper';
import { ThemeProvider, createTheme } from '@mui/material/styles';
import { CssBaseline, AppBar, Toolbar, Typography, Button, Box, Container } from '@mui/material';
import { ToastContainer } from 'react-toastify';

const App = inject('storeFI', 'themeStore')(observer(({ themeStore }) => {
    const { mode } = themeStore;
    const theme = React.useMemo(() => createTheme({ palette: { mode } }), [mode]);

    return (
        <ThemeProvider theme={theme}>
            <CssBaseline />
            <ToastContainer
                theme={mode === 'dark' ? 'dark' : 'light'}
                toastClassName={mode === 'dark' ? 'dark-toast' : ''}
            />

            <BrowserRouter>
                <AppBar position="static" sx={{ backgroundColor: mode === 'dark' ? '#1a1a1a' : '#333' }}>
                    <Toolbar>
                        <Typography variant="h6" sx={{ flexGrow: 1 }}>
                            <Link to="/" style={{ color: 'inherit', textDecoration: 'none' }}>
                                Images & Media Parser
                            </Link>
                        </Typography>
                        <Box sx={{ display: 'flex', gap: 2 }}>
                            <Button color="inherit" component={Link} to="/">Главная</Button>
                            <Button color="inherit" onClick={themeStore.toggleMode}>
                                {mode === 'dark' ? 'Светлая тема' : 'Тёмная тема'}
                            </Button>
                        </Box>
                    </Toolbar>
                </AppBar>

                <Container maxWidth={false} disableGutters sx={{ px: 2, py: 4 }}>
                    <Routes>
                        <Route path="/" element={<FWIStepper />} />
                    </Routes>
                </Container>
            </BrowserRouter>
        </ThemeProvider>
    );
}));

export default App;