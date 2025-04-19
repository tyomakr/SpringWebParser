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
    useScrollTrigger,
} from '@mui/material';

import FWIRequest from './fragments/FWIRequest';
import Gallery from './fragments/Gallery';
import PrepareToSendImages from './fragments/PrepareToSendImages';

/** Заголовки шагов */
const STEPS = ['Запрос изображений', 'Отбор изображений', 'Публикация'];

const FWIStepper = inject('storeFI')(observer(({ storeFI }) => {
    /* ――― фиксированная панель привязывается к AppBar ――― */
    const theme   = useTheme();
    const trigger = useScrollTrigger({ disableHysteresis: true, threshold: 0 });
    const appBarH = theme.mixins.toolbar.minHeight || 64;
    const headerH = 64;
    const headerTop = trigger ? 0 : appBarH;

    /* ――― локальное состояние шага для UI ――― */
    const [activeStep, setActiveStep] = React.useState(0);

    const next  = () => setActiveStep(s => Math.min(s + 1, STEPS.length - 1));
    const back  = () => setActiveStep(s => Math.max(s - 1, 0));
    const reset = () => { storeFI.clearStore(); setActiveStep(0); };

    const renderStep = () => {
        switch (activeStep) {
            case 0:  return <FWIRequest />;
            case 1:  return <Gallery />;
            case 2:  return <PrepareToSendImages />;
            default: return null;
        }
    };

    /* ――― условия «Далее» ――― */
    const canGoNext =
        (activeStep === 0 && storeFI.currentStep >= 1) ||
        (activeStep === 1 && storeFI.selectedImages.length > 0);

    return (
        <>
            {/* ────────────────────────────────────── фикс‑панель ────────────────────────────────────── */}
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
                    <span>Выбрано&nbsp;изображений:</span>
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

            {/* ────────────────────────────────────── контент ────────────────────────────────────── */}
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
                        <Step key={label}><StepLabel>{label}</StepLabel></Step>
                    ))}
                </Stepper>

                {renderStep()}

                <Box sx={{ display: 'flex', gap: 2, mt: 3 }}>
                    {activeStep > 0 && (
                        <Button variant="outlined" onClick={back}>
                            Назад
                        </Button>
                    )}

                    {activeStep < STEPS.length - 1 ? (
                        <Button
                            variant="contained"
                            onClick={next}
                            disabled={!canGoNext}
                        >
                            Далее
                        </Button>
                    ) : (
                        <Button
                            variant="contained"
                            color="success"
                            onClick={() =>
                                storeFI.saveAndPublishSelectedImages(storeFI.selectedImages)
                            }
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