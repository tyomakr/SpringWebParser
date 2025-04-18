// src/components/FWIStepper.js
import React from 'react';
import { inject, observer } from 'mobx-react';
import {
    Box,
    Container,
    Stepper,
    Step,
    StepLabel,
    Button,
    useTheme,
    useScrollTrigger
} from '@mui/material';
import FWIRequest from './fragments/FWIRequest';
import Gallery from './fragments/Gallery';
import PrepareToSendImages from './fragments/PrepareToSendImages';

function getSteps() {
    return ['Запрос изображений', 'Отбор изображений', 'Отправка изображений'];
}
function getStepContent(step) {
    switch (step) {
        case 0: return <FWIRequest />;
        case 1: return <Gallery />;
        case 2: return <PrepareToSendImages />;
        default: return null;
    }
}

const FWIStepper = inject('storeFI')(observer(({ storeFI }) => {
    const theme = useTheme();

    // здесь — сразу без гистерезиса и с порогом 0
    const trigger = useScrollTrigger({
        disableHysteresis: true,
        threshold: 0
    });

    const appBarHeight = theme.mixins.toolbar.minHeight || 64;
    const headerHeight = 64;
    // если хоть на 1px опустили вниз — “прилипает” наверх;
    // иначе остаётся сразу под AppBar
    const headerTop = trigger ? 0 : appBarHeight;

    const [activeStep, setActiveStep] = React.useState(0);
    const steps = getSteps();

    const handleNext  = () => setActiveStep(s => Math.min(s + 1, steps.length - 1));
    const handleBack  = () => setActiveStep(s => Math.max(s - 1, 0));
    const handleReset = () => { storeFI.clearStore(); setActiveStep(0); };

    return (
        <>
            {/* 1) Фиксированная панель "Выбрано изображений" */}
            <Box
                component="header"
                sx={{
                    position: 'fixed',
                    top: headerTop,
                    left: 0,
                    right: 0,
                    height: headerHeight,
                    bgcolor: 'background.paper',
                    borderBottom: 1,
                    borderColor: 'divider',
                    zIndex: theme.zIndex.appBar - 1
                }}
            >
                <Box
                    sx={{
                        height: '100%',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'space-between',
                        px: 2
                    }}
                >
                    <Box component="span">
                        Выбрано изображений: <b>{storeFI.selectedImages.length}</b>
                    </Box>
                    <Button
                        variant="contained"
                        color="warning"
                        disabled={activeStep === 0}
                        onClick={handleReset}
                    >
                        Сброс
                    </Button>
                </Box>
            </Box>

            {/* 2) Основной контейнер: сдвигаем вниз на AppBar + нашу панель */}
            <Container
                maxWidth={false}
                disableGutters
                sx={{
                    pt: `${appBarHeight + headerHeight}px`,
                    px: 2,
                    pb: 4,
                    overflowX: 'hidden'
                }}
            >
                {/* ЕДИНСТВЕННЫЙ степпер */}
                <Stepper nonLinear activeStep={activeStep} sx={{ mb: 3 }}>
                    {steps.map(label => (
                        <Step key={label}>
                            <StepLabel>{label}</StepLabel>
                        </Step>
                    ))}
                </Stepper>

                <Box sx={{ mb: 3 }}>
                    {getStepContent(activeStep)}
                </Box>

                <Box sx={{ display: 'flex', gap: 2 }}>
                    {activeStep > 0 && (
                        <Button variant="outlined" onClick={handleBack}>
                            Назад
                        </Button>
                    )}
                    {activeStep < steps.length - 1 ? (
                        <Button
                            variant="contained"
                            onClick={handleNext}
                            disabled={
                                (activeStep === 0 && !storeFI.step1) ||
                                (activeStep === 1 && storeFI.selectedImages.length === 0)
                            }
                        >
                            {activeStep === steps.length - 2 ? 'Подготовка к отправке' : 'Далее'}
                        </Button>
                    ) : (
                        <Button
                            variant="contained"
                            color="success"
                            onClick={() => storeFI.saveAndPublishSelectedImages(storeFI.selectedImages)}
                            disabled={storeFI.selectedImages.length === 0}
                        >
                            Отправить
                        </Button>
                    )}
                </Box>
            </Container>
        </>
    );
}));

export default FWIStepper;
