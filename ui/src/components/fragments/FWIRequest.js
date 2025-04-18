import React from 'react';
import { Helmet } from 'react-helmet';
import { inject, observer } from 'mobx-react';
import storeFI from '../../store/storeFI';
import { toast } from 'react-toastify';
import { useForm, Controller } from 'react-hook-form';
import * as yup from 'yup';
import { yupResolver } from '@hookform/resolvers/yup';
import {
    Box,
    TextField,
    Button,
    Typography,
    Container
} from '@mui/material';

// Схема валидации
const schema = yup.object({
    num1: yup
        .number().typeError('Должно быть числом')
        .integer('Только целые')
        .positive('> 0')
        .required('Обязательно'),
    num2: yup
        .number().typeError('Должно быть числом')
        .integer('Только целые')
        .positive('> 0')
        .required('Обязательно')
});

const FWIRequest = inject('mainStore', 'storeFI')(observer(() => {
    const { control, handleSubmit, formState: { errors, isSubmitting, isDirty } } = useForm({
        defaultValues: { num1: 1, num2: 20 },
        resolver: yupResolver(schema)
    });

    const onSubmit = async (values) => {
        try {
            await storeFI.getWebImagesFromPages(values.num1, values.num2);
            storeFI.step1 = true;
            const count = storeFI.webImages.length;
            toast.success(`Успешно! Найдено ${count} изображений.`, {
                position: 'bottom-right',
                autoClose: 5000,
                hideProgressBar: true,
            });
        } catch {
            toast.error('Проблема при запросе.', {
                position: 'bottom-right',
                autoClose: 5000,
            });
        }
    };

    return (
        <>
            <Helmet
                htmlAttributes={{ lang: 'ru' }}
                title="Запрос изображений"
                titleTemplate="Spring web parser - %s"
            />

            <Container maxWidth="sm" sx={{ py: 4 }}>
                <Typography
                    variant="h4"
                    component="h1"
                >
                    Запрос изображений для отбора
                </Typography>

                <Box
                    component="form"
                    onSubmit={handleSubmit(onSubmit)}
                    noValidate
                    sx={{
                        display: 'flex',
                        flexDirection: 'column',
                        gap: 2,
                        mt: 2
                    }}
                >
                    <Controller
                        name="num1"
                        control={control}
                        render={({ field }) => (
                            <TextField
                                {...field}
                                label="Страница от"
                                type="number"
                                fullWidth
                                error={!!errors.num1}
                                helperText={errors.num1?.message}
                            />
                        )}
                    />

                    <Controller
                        name="num2"
                        control={control}
                        render={({ field }) => (
                            <TextField
                                {...field}
                                label="Страница до"
                                type="number"
                                fullWidth
                                error={!!errors.num2}
                                helperText={errors.num2?.message}
                            />
                        )}
                    />

                    <Box sx={{ display: 'flex', gap: 2, mt: 2 }}>
                        <Button
                            type="submit"
                            variant="contained"
                            disabled={isSubmitting || !isDirty}
                        >
                            Отправить запрос
                        </Button>
                        <Button
                            type="button"
                            variant="outlined"
                            onClick={() => window.location.reload()}
                            disabled={isSubmitting}
                        >
                            Сбросить
                        </Button>
                    </Box>
                </Box>
            </Container>
        </>
    );
}));

export default FWIRequest;