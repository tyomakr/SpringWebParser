import React from "react";
import { BrowserRouter, Routes, Route, Link, useLocation } from "react-router-dom";
import { inject, observer } from "mobx-react";

import FWIStepper from "./components/FWIStepper";
import MlPublishPage from "./components/MlPublishPage";
import VkHistoryTrainingPage from "./pages/VkHistoryTrainingPage";

import { ThemeProvider, createTheme } from "@mui/material/styles";
import { CssBaseline, AppBar, Toolbar, Typography, Button, Box, Container } from "@mui/material";
import { ToastContainer } from "react-toastify";

const AppShell = observer(({ themeStore }) => {
    const { mode, toggleMode } = themeStore;
    const theme = React.useMemo(() => createTheme({ palette: { mode } }), [mode]);
    const location = useLocation();

    return (
        <ThemeProvider theme={theme}>
            <CssBaseline />
            <ToastContainer position="bottom-right" />

            <AppBar position="static">
                <Toolbar>
                    <Typography variant="h6" sx={{ flexGrow: 1 }}>
                        Spring Web Parser
                    </Typography>
                    <Box sx={{ display: "flex", gap: 1, mr: 2 }}>
                        <Button
                            color="inherit"
                            component={Link}
                            to="/"
                            variant={location.pathname === "/" ? "outlined" : "text"}
                        >
                            Ручной отбор
                        </Button>
                        <Button
                            color="inherit"
                            component={Link}
                            to="/ml-publish"
                            variant={location.pathname === "/ml-publish" ? "outlined" : "text"}
                        >
                            ML-публикация
                        </Button>
                        <Button
                            color="inherit"
                            component={Link}
                            to="/vk-history"
                            variant={location.pathname === "/vk-history" ? "outlined" : "text"}
                        >
                            VK история/обучение
                        </Button>
                    </Box>
                    <Button color="inherit" onClick={toggleMode}>
                        {mode === "dark" ? "Светлая тема" : "Темная тема"}
                    </Button>
                </Toolbar>
            </AppBar>

            <Container maxWidth={false} disableGutters sx={{ px: 2, py: 4 }}>
                <Routes>
                    <Route path="/" element={<FWIStepper />} />
                    <Route path="/ml-publish" element={<MlPublishPage />} />
                    <Route path="/vk-history" element={<VkHistoryTrainingPage />} />
                </Routes>
            </Container>
        </ThemeProvider>
    );
});

const App = inject("storeFI", "themeStore")(
    observer(({ themeStore }) => (
        <BrowserRouter>
            <AppShell themeStore={themeStore} />
        </BrowserRouter>
    ))
);

export default App;
