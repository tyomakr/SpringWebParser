import React from 'react';
import { Helmet } from 'react-helmet';
import { inject, observer } from 'mobx-react';
import { toast } from 'react-toastify';
import {
    ImageList,
    ImageListItem,
    ImageListItemBar,
    IconButton,
    Pagination,
    Button,
    Box,
    useMediaQuery
} from '@mui/material';
import { useTheme } from '@mui/material/styles';
import DeleteOutlined from '@mui/icons-material/Delete';
import LogConsole from '../LogConsole';

// DnD-kit
import {
    DndContext,
    PointerSensor,
    useSensor,
    useSensors,
    closestCenter
} from '@dnd-kit/core';
import {
    SortableContext,
    useSortable,
    rectSortingStrategy
} from '@dnd-kit/sortable';
import { CSS } from '@dnd-kit/utilities';

/**
 * Перетаскиваемый элемент галереи.
 * Использует поле directLink для загрузки изображения
 * и оборачивает кнопку удаления в тот же Box+IconButton,
 * что во втором шаге (Step2Gallery.js).
 */
const SortableImage = ({ image, index, onRemove }) => {
    const id = `${image.directLink}-${index}`;
    const {
        attributes,
        listeners,
        setNodeRef,
        transform,
        transition
    } = useSortable({ id });

    const style = {
        transform: CSS.Transform.toString(transform),
        transition
    };

    return (
        <ImageListItem
            ref={setNodeRef}
            {...attributes}
            {...listeners}
            style={style}
            key={id}
        >
            <img
                src={image.directLink}
                alt=""
                loading="lazy"
                style={{
                    width: '100%',
                    height: '100%',
                    objectFit: 'cover',
                    display: 'block'
                }}
            />
            <ImageListItemBar
                position="bottom"
                actionIcon={
                    <Box sx={{ display: 'flex' }}>
                        <IconButton
                            aria-label={`Удалить изображение ${index}`}
                            sx={{ color: '#fff' }}
                            onClick={() => onRemove(index)}
                        >
                            <DeleteOutlined />
                        </IconButton>
                    </Box>
                }
            />
        </ImageListItem>
    );
};

/**
 * Шаг 3: отображение галереи, drag&drop,
 * фильтрация дубликатов и кнопка «Отправить».
 */
const Step3PrepareSend = inject('storeFI')(observer(({ storeFI }) => {
    // DnD-датчики
    const sensors = useSensors(useSensor(PointerSensor));
    const theme = useTheme();
    const is4k = useMediaQuery('(min-width:2560px)');
    const isFHD = useMediaQuery('(min-width:1920px)');
    const isXl = useMediaQuery(theme.breakpoints.up('xl'));
    const isLg = useMediaQuery(theme.breakpoints.up('lg'));
    const isMd = useMediaQuery(theme.breakpoints.up('md'));
    const isSm = useMediaQuery(theme.breakpoints.up('sm'));

    // Вычисляем число колонок под любые разрешения
    let cols = 1;
    if (is4k)      cols = 10;
    else if (isFHD) cols = 6;
    else if (isXl)  cols = 5;
    else if (isLg)  cols = 4;
    else if (isMd)  cols = 3;
    else if (isSm)  cols = 2;

    // Перетаскивание
    const handleDragEnd = event => {
        const { active, over } = event;
        if (over && active.id !== over.id) {
            const fromIndex = storeFI.selectedImages.findIndex(
                (img, idx) => `${img.directLink}-${idx}` === active.id
            );
            const toIndex = storeFI.selectedImages.findIndex(
                (img, idx) => `${img.directLink}-${idx}` === over.id
            );
            storeFI.reorderSelectedImages(fromIndex, toIndex);
        }
    };

    // Отправка с фильтрацией дубликатов
    const handleSend = () => {
        if (!storeFI.selectedImages.length) return;
        const unique = storeFI.selectedImages.filter(
            (img, idx, arr) =>
                arr.findIndex(x => x.directLink === img.directLink) === idx
        );
        storeFI
            .saveAndPublishSelectedImages(unique)
            .then(res =>
                toast.success(res, {
                    position: 'bottom-right',
                    autoClose: 15000,
                    hideProgressBar: true
                })
            )
            .catch(err =>
                toast.error(`Ошибка при отправке: ${err.message}`, {
                    position: 'bottom-right'
                })
            );
    };

    // Удаление по индексу
    const handleRemove = idx => {
        storeFI.removeSelectedImageByIndex(idx);
    };

    // Пагинация
    const pageSize = storeFI.pageSize || 40;
    const pageCount = Math.ceil(storeFI.selectedImages.length / pageSize);

    return (
        <>
            <Helmet>
                <title>Подготовка к отправке</title>
            </Helmet>

            <ImageList
                variant="quilted"
                cols={cols}
                gap={4}
                rowHeight={164}
            >
                <DndContext
                    sensors={sensors}
                    collisionDetection={closestCenter}
                    onDragEnd={handleDragEnd}
                >
                    <SortableContext
                        items={storeFI.selectedImages.map(
                            (img, idx) => `${img.directLink}-${idx}`
                        )}
                        strategy={rectSortingStrategy}
                    >
                        {storeFI.selectedImages.map((wi, idx) => (
                            <SortableImage
                                key={`${wi.directLink}-${idx}`}
                                image={wi}
                                index={idx}
                                onRemove={handleRemove}
                            />
                        ))}
                    </SortableContext>
                </DndContext>
            </ImageList>

            <Pagination
                count={pageCount}
                page={storeFI.page}
                onChange={(_, v) => (storeFI.page = v)}
                sx={{ mt: 2 }}
                aria-label="Навигация по страницам подготовленных изображений"
            />

            <Button
                variant="contained"
                onClick={handleSend}
                disabled={!storeFI.selectedImages.length}
                sx={{ mt: 2 }}
            >
                Отправить
            </Button>

            <LogConsole skipCache />
        </>
    );
}));

export default Step3PrepareSend;