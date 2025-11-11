import React from 'react';
import { Helmet } from 'react-helmet';
import { inject, observer } from 'mobx-react';
import { toast } from 'react-toastify';
import { useForm, Controller } from 'react-hook-form';
import * as yup from 'yup';
import { yupResolver } from '@hookform/resolvers/yup';
import {
    Box,
    TextField,
    Button,
    Typography,
    Container,
} from '@mui/material';
import LogConsole from '../LogConsole';

// Схема валидации
const schema = yup.object({
    num1: yup
        .number()
        .typeError('Должно быть числом')
        .integer('Только целые')
        .positive('> 0')
        .required('Обязательно'),
    num2: yup
        .number()
        .typeError('Должно быть числом')
        .integer('Только целые')
        .positive('> 0')
        .required('Обязательно'),
});

const Step1GetImages = inject('storeFI')(
    observer(({ storeFI }) => {
        const {
            control,
            handleSubmit,
            reset,
            formState: { errors, isSubmitting, isValid },
        } = useForm({
            defaultValues: { num1: 1, num2: 20 },
            resolver: yupResolver(schema),
            mode: 'onChange',
            reValidateMode: 'onChange',
        });

        const onSubmit = async (values) => {
            try {
                await storeFI.getWebImagesFromPages(values.num1, values.num2);
                toast.success(`Успешно! Найдено ${storeFI.webImages.length} изображений.`, {
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

        // Локальный сброс: только поля формы и webImages/step1
        const handleReset = () => {
            // 1) вернуть поля к дефолту
            reset({ num1: 1, num2: 20 });
            // 2) очистить результаты предыдущего запроса
            storeFI.clearWebImages();
        };

        return (
            <>
                <Helmet
                    htmlAttributes={{ lang: 'ru' }}
                    title="Запрос изображений"
                    titleTemplate="Spring web parser - %s"
                />
                <Container maxWidth="sm" sx={{ py: 4 }}>
                    <Typography variant="h4" component="h1" gutterBottom>
                        Запрос изображений для отбора
                    </Typography>
                    <Box
                        component="form"
                        onSubmit={handleSubmit(onSubmit)}
                        noValidate
                        sx={{ display: 'flex', flexDirection: 'column', gap: 2, mt: 2 }}
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
                                disabled={isSubmitting || !isValid}
                            >
                                Отправить запрос
                            </Button>
                            <Button
                                type="button"
                                variant="outlined"
                                onClick={handleReset}
                                disabled={isSubmitting}
                            >
                                Сбросить
                            </Button>
                        </Box>
                    </Box>

                    {/* Логи процесса */}
                    <Box mt={4}>
                        <Typography variant="h6" gutterBottom>
                            Логи процесса
                        </Typography>
                        <LogConsole skipCache />
                    </Box>
                </Container>
            </>
        );
    })
);

export default Step1GetImages;