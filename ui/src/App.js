import React from 'react';
import { Link, Route, BrowserRouter, Routes } from 'react-router-dom';
import MainPage from './components/pages/MainPage';
import FWIStepper from './components/FWIStepper';
import { inject, observer } from 'mobx-react';
import themeStore from './store/themeStore';
import { ToastContainer } from 'react-toastify';
import 'react-toastify/dist/ReactToastify.css';
import { ThemeProvider, createTheme } from '@mui/material/styles';
import {
    CssBaseline,
    AppBar,
    Toolbar,
    Typography,
    Button,
    Box,
    Container
} from '@mui/material';

const App = inject('mainStore', 'storeFI', 'themeStore')(observer(() => {
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
                {/* Шапка */}
                <AppBar position="static" sx={{ backgroundColor: mode === 'dark' ? '#1a1a1a' : '#333' }}>
                    <Toolbar>
                        <Typography variant="h6" sx={{ flexGrow: 1 }}>
                            <Link to="/" style={{ color: 'inherit', textDecoration: 'none' }}>
                                Images & Media Parser
                            </Link>
                        </Typography>
                        <Box sx={{ display: 'flex', gap: 2 }}>
                            <Button color="inherit" component={Link} to="/">Главная</Button>
                            <Button color="inherit" component={Link} to="/fwi-stepper">Изображения</Button>
                            <Button color="inherit" onClick={themeStore.toggleMode}>
                                {mode === 'dark' ? 'Светлая тема' : 'Тёмная тема'}
                            </Button>
                        </Box>
                    </Toolbar>
                </AppBar>

                {/* Контент на всю ширину */}
                <Container
                    maxWidth={false}
                    disableGutters
                    sx={{ px: 2, py: 4 }}
                >
                    <Routes>
                        <Route path="/" element={<MainPage />} />
                        <Route path="/fwi-stepper" element={<FWIStepper />} />
                    </Routes>
                </Container>
            </BrowserRouter>
        </ThemeProvider>
    );
}));

export default App;
