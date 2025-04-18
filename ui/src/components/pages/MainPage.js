import React from 'react';
import { Helmet } from 'react-helmet/es/Helmet';
import { inject, observer } from 'mobx-react';
import { Box, Container, Typography } from '@mui/material';

const MainPage = inject('mainStore', 'storeFI')(observer(() => {
    return (
        <>
            <Helmet
                htmlAttributes={{ lang: 'ru' }}
                title="Главная"
                titleTemplate="Spring web parser - %s"
            />

            <Box sx={{ py: 4, bgcolor: 'background.default' }}>
                <Container maxWidth="sm" sx={{ px: 2 }}>
                    <Typography
                        variant="h4"
                        component="h1"
                        gutterBottom
                    >
                        Система парсинга данных
                    </Typography>
                    <Typography>
                        Тут пока пустая главная страница
                    </Typography>
                </Container>
            </Box>
        </>
    );
}));

export default MainPage;
