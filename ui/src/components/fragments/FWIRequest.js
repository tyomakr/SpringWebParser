import React from 'react';
import { Helmet } from 'react-helmet';
import { inject, observer } from 'mobx-react';
import storeFI from '../../store/storeFI';
import { toast } from 'react-toastify';
import { useForm, Controller } from 'react-hook-form';
import {
    Box,
    TextField,
    Button,
    Typography,
    Container
} from '@mui/material';

const FWIRequest = inject('mainStore', 'storeFI')(observer(() => {
    const {
        control,
        handleSubmit,
        reset,
        formState: { errors, isValid, isDirty, isSubmitting }
    } = useForm({
        defaultValues: { num1: '1', num2: '20' },
        mode: 'onChange'
    });

    const onSubmit = async (values) => {
        try {
            await storeFI.getWebImagesFromPages(values.num1, values.num2);
            storeFI.step1 = true;
            toast.success(`Успешно! Найдено ${storeFI.webImages.length} изображений`, {
                position: 'bottom-right', autoClose: 5000
            });
        } catch {
            toast.error('Проблема: сервис недоступен', {
                position: 'bottom-right', autoClose: 5000
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
                <Typography variant="h4" component="h1" gutterBottom>
                    Запрос изображений для отбора
                </Typography>

                {/* явно объявляем форму */}
                <Box
                    component="form"
                    role="form"
                    noValidate
                    autoComplete="off"
                    onSubmit={handleSubmit(onSubmit)}
                    sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}
                >
                    <Controller
                        name="num1"
                        control={control}
                        rules={{
                            required: 'Обязательное поле',
                            pattern: { value: /^[1-9]\d*$/, message: 'Только натуральные числа' }
                        }}
                        render={({ field }) => (
                            <TextField
                                {...field}
                                label="Страница от"
                                type="number"
                                fullWidth
                                error={!!errors.num1}
                                helperText={errors.num1?.message}
                                aria-invalid={!!errors.num1}
                            />
                        )}
                    />

                    <Controller
                        name="num2"
                        control={control}
                        rules={{
                            required: 'Обязательное поле',
                            pattern: { value: /^[1-9]\d*$/, message: 'Только натуральные числа' }
                        }}
                        render={({ field }) => (
                            <TextField
                                {...field}
                                label="Страница до"
                                type="number"
                                fullWidth
                                error={!!errors.num2}
                                helperText={errors.num2?.message}
                                aria-invalid={!!errors.num2}
                            />
                        )}
                    />

                    <Box sx={{ display: 'flex', gap: 2, mt: 2 }}>
                        <Button
                            type="submit"
                            variant="contained"
                            disabled={!isValid || isSubmitting}
                            aria-disabled={!isValid || isSubmitting}
                        >
                            Отправить запрос
                        </Button>
                        <Button
                            type="button"
                            variant="outlined"
                            onClick={() => reset()}
                            disabled={!isDirty || isSubmitting}
                            aria-disabled={!isDirty || isSubmitting}
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
