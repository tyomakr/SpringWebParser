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
    useScrollTrigger,
} from '@mui/material';

import FWIRequest from './fragments/FWIRequest';
import Gallery from './fragments/Gallery';
import PrepareToSendImages from './fragments/PrepareToSendImages';

/** Заголовки шагов */
const STEPS = ['Запрос изображений', 'Отбор изображений', 'Публикация'];

const FWIStepper = inject('storeFI')(observer(({ storeFI }) => {
    const theme = useTheme();
    const trigger = useScrollTrigger({ disableHysteresis: true, threshold: 0 });
    const appBarH = theme.mixins.toolbar.minHeight || 64;
    const headerH = 64;
    const headerTop = trigger ? 0 : appBarH;

    const [activeStep, setActiveStep] = React.useState(0);

    const next = () => setActiveStep(s => Math.min(s + 1, STEPS.length - 1));
    const back = () => setActiveStep(s => Math.max(s - 1, 0));
    const reset = () => { storeFI.clearStore(); setActiveStep(0); };

    const renderStep = () => {
        switch (activeStep) {
            case 0: return <FWIRequest />;
            case 1: return <Gallery />;
            case 2: return <PrepareToSendImages />;
            default: return null;
        }
    };

    /** Условия для кнопки «Далее» */
    const canGoNext =
        (activeStep === 0 && storeFI.currentStep >= 1) ||
        (activeStep === 1 && storeFI.selectedImages.length > 0);

    return (
        <>
            {/* Фикс‑панель с количеством выбранных изображений и кнопкой «Сброс» */}
            <Box
                sx={{
                    position: 'fixed',
                    top: headerTop,
                    left: 0,
                    right: 0,
                    height: headerH,
                    bgcolor: 'background.paper',
                    borderBottom: 1,
                    borderColor: 'divider',
                    zIndex: theme.zIndex.appBar - 1,
                    px: 2,
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                }}
            >
                <Box sx={{ display: 'flex', alignItems: 'baseline', gap: 0.5 }}>
                    <span>Выбрано изображений:</span>
                    <b>{storeFI.selectedImages.length}</b>
                </Box>
                <Button
                    variant="outlined"
                    color="warning"
                    disabled={activeStep === 0}
                    onClick={reset}
                >
                    Сброс
                </Button>
            </Box>

            {/* Основной контент */}
            <Container
                maxWidth={false}
                disableGutters
                sx={{
                    pt: `${appBarH + headerH}px`,
                    px: 2,
                    pb: 4,
                }}
            >
                <Stepper nonLinear activeStep={activeStep} sx={{ mb: 3 }}>
                    {STEPS.map(label => (
                        <Step key={label}>
                            <StepLabel>{label}</StepLabel>
                        </Step>
                    ))}
                </Stepper>

                {renderStep()}

                {/* Навигационные кнопки */}
                <Box sx={{ display: 'flex', gap: 2, mt: 3 }}>
                    {/* Назад — на всех шагах, кроме первого */}
                    {activeStep > 0 && (
                        <Button variant="outlined" onClick={back}>
                            Назад
                        </Button>
                    )}

                    {/* Далее — только на шагах 0 и 1 */}
                    {activeStep < STEPS.length - 1 && (
                        <Button
                            variant="contained"
                            onClick={next}
                            disabled={!canGoNext}
                        >
                            Далее
                        </Button>
                    )}
                </Box>
            </Container>
        </>
    );
}));

export default FWIStepper;