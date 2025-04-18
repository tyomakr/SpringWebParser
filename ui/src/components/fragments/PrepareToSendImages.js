import React from 'react';
import { Helmet } from 'react-helmet/es/Helmet';
import { inject, observer } from 'mobx-react';
import { toast } from 'react-toastify';
import {
    Box,
    Container,
    ImageList,
    ImageListItem,
    ImageListItemBar,
    IconButton,
    Pagination,
    Stack,
    Typography,
    Button,
    useMediaQuery
} from '@mui/material';
import { useTheme } from '@mui/material/styles';
import DeleteOutlined from '@mui/icons-material/Delete';

const PrepareToSendImages = inject('storeFI')(observer(({ storeFI }) => {
    // Отправка
    const handleSend = () => {
        if (storeFI.selectedImages.length) {
            storeFI
                .saveAndPublishSelectedImages(storeFI.selectedImages)
                .then(res =>
                    toast.success(res, {
                        position: 'bottom-right',
                        autoClose: 15000,
                        hideProgressBar: true
                    })
                );
        }
    };

    // Удаление
    const handleRemove = idx => {
        storeFI.removeSelectedImageByIndex(idx);
    };

    // Пагинация
    const pageSize  = storeFI.pageSize || 40;
    const pageCount = Math.ceil(storeFI.selectedImages.length / pageSize);

    // Колонки по брейкпоинтам
    const theme  = useTheme();
    const is4k   = useMediaQuery('(min-width:2560px)');
    const isFHD  = useMediaQuery('(min-width:1920px)');
    const isXl   = useMediaQuery(theme.breakpoints.up('xl'));
    const isLg   = useMediaQuery(theme.breakpoints.up('lg'));
    const isMd   = useMediaQuery(theme.breakpoints.up('md'));
    const isSm   = useMediaQuery(theme.breakpoints.up('sm'));

    let cols = 1;
    if      (is4k)   cols = 10;  // ▶︎ ровно 10 колонок на 4K
    else if (isFHD)  cols = 6;
    else if (isXl)   cols = 5;
    else if (isLg)   cols = 4;
    else if (isMd)   cols = 3;
    else if (isSm)   cols = 2;

    return (
        <>
            <Helmet
                htmlAttributes={{ lang: 'ru' }}
                title="Подготовка к отправке..."
                titleTemplate="Spring web parser - %s"
            />

            <Box sx={{ py: 4, bgcolor: 'background.default' }}>
                <Container maxWidth={false} disableGutters sx={{ px: 2 }}>
                    <Typography variant="h4" component="h1" gutterBottom>
                        Подготовка к отправке
                    </Typography>

                    {/* Сетка квадратных эскизов */}
                    <ImageList
                        cols={cols}
                        rowHeight="auto"
                        gap={1}
                        sx={{ width: '100%' }}
                        aria-label="Список выбранных изображений"
                    >
                        {storeFI.selectedImages.map((wi, idx) => (
                            <ImageListItem key={idx}>
                                <Box
                                    sx={{
                                        position: 'relative',
                                        width: '100%',
                                        pt: '100%',
                                        overflow: 'hidden',
                                        borderRadius: 1
                                    }}
                                >
                                    <Box
                                        component="img"
                                        src={wi.directLink}
                                        alt={`Изображение ${wi.id}`}
                                        loading="lazy"
                                        sx={{
                                            position: 'absolute',
                                            top: 0, left: 0,
                                            width: '100%',
                                            height: '100%',
                                            objectFit: 'cover'
                                        }}
                                    />
                                </Box>
                                <ImageListItemBar
                                    position="bottom"
                                    actionIcon={
                                        <IconButton
                                            aria-label={`Удалить изображение ${wi.id}`}
                                            sx={{ color: '#fff' }}
                                            onClick={() => handleRemove(idx)}
                                        >
                                            <DeleteOutlined />
                                        </IconButton>
                                    }
                                />
                            </ImageListItem>
                        ))}
                    </ImageList>

                    {/* Увеличенный шрифт и gap у пагинации */}
                    <Stack alignItems="center" spacing={2} sx={{ my: 2 }}>
                        <Pagination
                            count={pageCount}
                            page={1}
                            size="medium"
                            sx={{
                                '& .MuiPaginationItem-root': {
                                    fontSize: '1.4rem',
                                    minWidth: '2.5rem',
                                    height: '2.5rem'
                                },
                                '& .MuiPagination-ul': {
                                    columnGap: '16px',
                                    rowGap: '16px'
                                }
                            }}
                            aria-label="Навигация по страницам подготовленных изображений"
                        />
                    </Stack>

                    {/* Единственная кнопка Отправить */}
                    <Box sx={{ textAlign: 'center' }}>
                        <Button
                            variant="contained"
                            color="success"
                            onClick={handleSend}
                            disabled={storeFI.selectedImages.length === 0}
                            aria-disabled={storeFI.selectedImages.length === 0}
                        >
                            Отправить
                        </Button>
                    </Box>
                </Container>
            </Box>
        </>
    );
}));

export default PrepareToSendImages;