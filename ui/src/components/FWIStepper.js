// src/components/FWIStepper.js
import React from "react";
import { inject, observer } from "mobx-react";
import {
    Box,
    Container,
    Stepper,
    Step,
    StepLabel,
    Button,
    useTheme,
    useScrollTrigger,
} from "@mui/material";

import Step1GetImages from "./fragments/Step1GetImages";
import Step2Gallery from "./fragments/Step2Gallery";
import Step3PrepareSend from "./fragments/Step3PrepareSend";

/** Заголовки шагов */
const STEPS = ["Запрос изображений", "Отбор изображений", "Публикация"];

const FWIStepper = inject("storeFI")(
    observer(({ storeFI }) => {
        const theme = useTheme();
        const trigger = useScrollTrigger({ disableHysteresis: true, threshold: 0 });
        const appBarH = theme.mixins.toolbar.minHeight || 64;
        const headerH = 64;
        const headerTop = trigger ? 0 : appBarH;

        const [activeStep, setActiveStep] = React.useState(0);
        const handleNext = () =>
            setActiveStep((s) => Math.min(s + 1, STEPS.length - 1));
        const handleBack = () =>
            setActiveStep((s) => Math.max(s - 1, 0));
        const handleReset = () => {
            storeFI.clearStore();
            setActiveStep(0);
        };

        // условия для кнопки «Далее»
        const canGoNext =
            activeStep === 0
                ? storeFI.step1
                : activeStep === 1
                    ? storeFI.selectedImages.length > 0
                    : false;

        const renderStepContent = () => {
            switch (activeStep) {
                case 0:
                    return <Step1GetImages />;
                case 1:
                    return <Step2Gallery />;
                case 2:
                    return <Step3PrepareSend />;
                default:
                    return null;
            }
        };

        return (
            <>
                {/* Фиксированная панель с количеством выбранных */}
                <Box
                    sx={{
                        position: "fixed",
                        top: headerTop,
                        left: 0,
                        right: 0,
                        height: headerH,
                        bgcolor: "background.paper",
                        borderBottom: 1,
                        borderColor: "divider",
                        zIndex: theme.zIndex.appBar - 1,
                        px: 2,
                        display: "flex",
                        alignItems: "center",
                        justifyContent: "space-between",
                    }}
                >
                    <Box sx={{ display: "flex", alignItems: "baseline", gap: 0.5 }}>
                        <span>Выбрано изображений:</span>
                        <b>{storeFI.selectedImages.length}</b>
                    </Box>
                    <Button
                        variant="outlined"
                        color="warning"
                        disabled={activeStep === 0}
                        onClick={handleReset}
                    >
                        Сброс
                    </Button>
                </Box>

                {/* Основной контейнер */}
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
                        {STEPS.map((label) => (
                            <Step key={label}>
                                <StepLabel>{label}</StepLabel>
                            </Step>
                        ))}
                    </Stepper>

                    {renderStepContent()}

                    <Box sx={{ display: "flex", gap: 2, mt: 3 }}>
                        {activeStep > 0 && (
                            <Button variant="outlined" onClick={handleBack}>
                                Назад
                            </Button>
                        )}

                        {/* только кнопка «Далее» на первых двух шагах */}
                        {activeStep < STEPS.length - 1 && (
                            <Button
                                variant="contained"
                                onClick={handleNext}
                                disabled={!canGoNext}
                            >
                                Далее
                            </Button>
                        )}
                        {/* на третьем шаге кнопок из степпера больше нет */}
                    </Box>
                </Container>
            </>
        );
    })
);

export default FWIStepper;