import React from 'react';
import { BrowserRouter, Routes, Route, Link } from 'react-router-dom';
import { inject, observer } from 'mobx-react';
import { createTheme, ThemeProvider } from '@mui/material/styles';
import {
    CssBaseline,
    AppBar,
    Toolbar,
    Typography,
    Button,
    Slide,
    useScrollTrigger,
    Container
} from '@mui/material';
import { ToastContainer } from 'react-toastify';
import 'react-toastify/dist/ReactToastify.css';
import themeStore from './store/themeStore';
import MainPage from './components/pages/MainPage';
import FWIStepper from './components/FWIStepper';
import './common/App.css';

// Хук для скрытия при скролле
function HideOnScroll({ children }) {
    const trigger = useScrollTrigger();
    return (
        <Slide appear={false} direction="down" in={!trigger}>
            {children}
        </Slide>
    );
}

const App = inject('mainStore', 'storeFI', 'themeStore')(observer(() => {
    const { mode } = themeStore;
    const theme = React.useMemo(
        () => createTheme({ palette: { mode } }),
        [mode]
    );

    return (
        <ThemeProvider theme={theme}>
            <CssBaseline />

            <BrowserRouter>

                {/* AppBar, скрывающийся при скролле */}
                <HideOnScroll>
                    <AppBar position="fixed" sx={{ backgroundColor: mode === 'dark' ? '#1a1a1a' : '#333' }}>
                        <Toolbar>
                            <Typography variant="h6" sx={{ flexGrow: 1 }}>
                                <Link to="/" style={{ color: 'inherit', textDecoration: 'none' }}>
                                    Images & Media Parser
                                </Link>
                            </Typography>
                            <Button color="inherit" component={Link} to="/">Главная</Button>
                            <Button color="inherit" component={Link} to="/fwi-stepper">Изображения</Button>
                            <Button color="inherit" onClick={themeStore.toggleMode}>
                                {mode === 'dark' ? 'Светлая тема' : 'Тёмная тема'}
                            </Button>
                        </Toolbar>
                    </AppBar>
                </HideOnScroll>

                {/* отступ для контента под AppBar */}
                <Toolbar />

                <ToastContainer
                    theme={mode === 'dark' ? 'dark' : 'light'}
                    toastClassName={mode === 'dark' ? 'dark-toast' : ''}
                />

                <Container sx={{ py: 4 }}>
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
