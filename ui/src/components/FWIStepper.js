import React from 'react';
import { inject, observer } from 'mobx-react';
import {
    Box,
    Container,
    Stepper,
    Step,
    StepLabel,
    Button,
    Typography,
    useTheme,
    useScrollTrigger
} from '@mui/material';
import FWIRequest from './fragments/FWIRequest';
import Gallery from './fragments/Gallery';
import PrepareToSendImages from './fragments/PrepareToSendImages';
import '../common/App.css';

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
    const trigger = useScrollTrigger();               // true, когда AppBar уже спрятался
    const toolbarHeight = theme.mixins.toolbar.minHeight || 64;
    const headerHeight  = 56;

    // если AppBar уехал — наш header прилипает к верху (top=0),
    // иначе остаётся под AppBar (top = toolbarHeight)
    const headerTop = trigger ? 0 : toolbarHeight;

    const [activeStep, setActiveStep] = React.useState(0);
    const steps = getSteps();
    const handleNext     = () => setActiveStep(s => Math.min(s + 1, steps.length - 1));
    const handleBack     = () => setActiveStep(s => Math.max(s - 1, 0));
    const handleResetAll = () => { storeFI.clearStore(); setActiveStep(0); };

    return (
        <>
            {/* 1) Fixed‑header «Выбрано / Сброс» */}
            <Box
                sx={{
                    position: 'fixed',
                    top: headerTop,
                    left: 0,
                    right: 0,
                    height: headerHeight,
                    zIndex: theme.zIndex.appBar - 1,
                    bgcolor: 'background.paper',
                    borderBottom: 1,
                    borderColor: 'divider'
                }}
            >
                <Box
                    className="header-info-selected-images-line"
                    sx={{ height: '100%' }}
                >
                    <Typography component="span">
                        Выбрано изображений: <b>{storeFI.selectedImages.length}</b>
                    </Typography>
                    <Button
                        variant="contained"
                        color="warning"
                        onClick={handleResetAll}
                        disabled={activeStep === 0}
                    >
                        Сброс
                    </Button>
                </Box>
            </Box>

            {/* 2) Основной контент, сдвинутый вниз на AppBar+header */}
            <Container
                maxWidth={false}
                disableGutters
                sx={{
                    pt: `${toolbarHeight + headerHeight}px`,
                    px: 2,
                    py: 4,
                    overflowX: 'hidden'
                }}
            >
                <Box sx={{ mt: 1 }}>
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
                        {activeStep < steps.length - 1 && (
                            <Button
                                variant="contained"
                                onClick={handleNext}
                                disabled={
                                    (activeStep === 0 && !storeFI.step1) ||
                                    (activeStep === 1 && storeFI.selectedImages.length === 0)
                                }
                            >
                                {activeStep === steps.length - 2
                                    ? 'Подготовка к отправке'
                                    : 'Далее'}
                            </Button>
                        )}
                    </Box>
                </Box>
            </Container>
        </>
    );
}));

export default FWIStepper;
