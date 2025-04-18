import React, { useState, useEffect } from 'react';
import { Helmet } from 'react-helmet';
import { inject, observer } from 'mobx-react';
import {
    Box,
    Container,
    Typography,
    ImageList,
    ImageListItem,
    ImageListItemBar,
    IconButton,
    Pagination,
    Stack,
    useMediaQuery,
    Skeleton
} from '@mui/material';
import { useTheme } from '@mui/material/styles';
import ZoomInIcon from '@mui/icons-material/ZoomIn';
import AddIcon from '@mui/icons-material/Add';
import Portal from '../Portal';

const Gallery = inject('storeFI')(observer(({ storeFI }) => {
    const [page, setPage] = useState(1);
    const [items, setItems] = useState([]);
    const [pageItems, setPageItems] = useState([]);
    const [selectedImage, setSelectedImage] = useState(null);
    const [loading, setLoading] = useState(false);

    const pageSize  = storeFI.pageSize || 40;
    const pageCount = Math.ceil(items.length / pageSize);

    useEffect(() => {
        // старт загрузки
        setLoading(true);
        const all = storeFI.webImages.slice();
        setItems(all);
        const slice = all.slice((page - 1) * pageSize, page * pageSize);
        setPageItems(slice);
        // завершение загрузки
        setLoading(false);
    }, [storeFI.webImages, page, pageSize]);

    const theme  = useTheme();
    const is4k   = useMediaQuery('(min-width:2560px)');
    const isFHD  = useMediaQuery('(min-width:1920px)');
    const isXl   = useMediaQuery(theme.breakpoints.up('xl'));
    const isLg   = useMediaQuery(theme.breakpoints.up('lg'));
    const isMd   = useMediaQuery(theme.breakpoints.up('md'));
    const isSm   = useMediaQuery(theme.breakpoints.up('sm'));

    let cols = 1;
    if      (is4k)  cols = 7;
    else if (isFHD) cols = 6;
    else if (isXl)  cols = 5;
    else if (isLg)  cols = 4;
    else if (isMd)  cols = 3;
    else if (isSm)  cols = 2;

    const handlePageChange = (_e, v) => {
        setPage(v);
        window.scrollTo(0, 0);
    };

    const select = wi => storeFI.selectedImages.push(wi);

    return (
        <>
            <Helmet
                htmlAttributes={{ lang: 'ru' }}
                title="Галерея и выбор изображений"
                titleTemplate="Spring web parser - %s"
            />

            <Box sx={{ py: 4, bgcolor: 'background.default' }}>
                <Container maxWidth={false} disableGutters sx={{ px: 2 }}>
                    <Typography variant="h4" component="h1" gutterBottom>
                        Просмотр и выбор изображений
                    </Typography>

                    {/* пагинация сверху */}
                    <Stack alignItems="center" spacing={1} sx={{ mb: 2 }}>
                        <Pagination
                            count={pageCount}
                            page={page}
                            onChange={handlePageChange}
                            size="small"
                            aria-label="Навигация по страницам галереи"
                        />
                    </Stack>

                    {/* skeleton или сетка */}
                    {loading ? (
                        <Box
                            key={page}
                            sx={{
                                display: 'grid',
                                gridTemplateColumns: `repeat(${cols}, 1fr)`,
                                gap: 1
                            }}
                        >
                            {Array.from({ length: cols * 2 }).map((_, idx) => (
                                <Skeleton
                                    key={idx}
                                    variant="rectangular"
                                    height={200}
                                />
                            ))}
                        </Box>
                    ) : (
                        <ImageList
                            key={page}
                            cols={cols}
                            rowHeight="auto"
                            gap={1}
                            sx={{ width: '100%' }}
                            aria-label="Список миниатюр"
                        >
                            {pageItems.map(wi => (
                                <ImageListItem key={wi.id}>
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
                                            <Box sx={{ display: 'flex' }}>
                                                <IconButton
                                                    aria-label={`Увеличить изображение ${wi.id}`}
                                                    sx={{ color: '#fff' }}
                                                    onClick={e => {
                                                        e.stopPropagation();
                                                        setSelectedImage(wi.directLink);
                                                    }}
                                                >
                                                    <ZoomInIcon />
                                                </IconButton>
                                                <IconButton
                                                    aria-label={`Выбрать изображение ${wi.id}`}
                                                    sx={{ color: '#fff' }}
                                                    onClick={() => select(wi)}
                                                >
                                                    <AddIcon />
                                                </IconButton>
                                            </Box>
                                        }
                                    />
                                </ImageListItem>
                            ))}
                        </ImageList>
                    )}

                    {/* пагинация снизу */}
                    <Stack alignItems="center" spacing={1} sx={{ mt: 2 }}>
                        <Pagination
                            count={pageCount}
                            page={page}
                            onChange={handlePageChange}
                            size="small"
                            aria-label="Навигация по страницам галереи"
                        />
                    </Stack>
                </Container>
            </Box>

            {selectedImage && (
                <Portal>
                    <Box
                        role="dialog"
                        aria-modal="true"
                        aria-labelledby="modal-title"
                        sx={{
                            position: 'fixed', inset: 0,
                            bgcolor: 'rgba(0,0,0,0.7)',
                            display: 'flex',
                            justifyContent: 'center',
                            alignItems: 'center',
                            px: 2,
                            zIndex: theme => theme.zIndex.modal
                        }}
                        onClick={() => setSelectedImage(null)}
                    >
                        <Typography
                            id="modal-title"
                            component="h2"
                            sx={{
                                position: 'absolute',
                                width: 1,
                                height: 1,
                                overflow: 'hidden',
                                clip: 'rect(0,0,0,0)'
                            }}
                        >
                            Увеличенное изображение
                        </Typography>
                        <Box
                            component="img"
                            src={selectedImage}
                            alt="Изображение в полном размере"
                            sx={{
                                maxWidth: '90vw',
                                maxHeight: '90vh',
                                objectFit: 'contain',
                                borderRadius: 1
                            }}
                        />
                    </Box>
                </Portal>
            )}
        </>
    );
}));

export default Gallery;