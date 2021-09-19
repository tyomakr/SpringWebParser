import React from 'react';
import {Box, Stepper} from "@mui/material";
import {Step} from "@mui/material";
import {StepLabel} from "@mui/material";
import {Button} from "@mui/material"
import {Typography} from "@mui/material";
import FWIRequest from "../components/fragments/FWIRequest"
import {inject, observer} from 'mobx-react';
import Gallery from "./fragments/Gallery";
import PrepareToSendImages from "./fragments/PrepareToSendImages";


function getSteps() {
    return ['Запрос изображений', 'Отбор изображений', 'Отправка изображений'];
}

function getStepContent(step) {
    switch (step) {
        case 0:
            return <FWIRequest/>;
        case 1:
            return <Gallery/>;
        case 2:
            return <PrepareToSendImages/>;
        default:
            return 'Unknown step';
    }
}


const ImageStepper = inject("storeFI")(observer((props) => {
    // const classes = useStyles();
    const [activeStep, setActiveStep] = React.useState(0);
    const steps = getSteps();

    const handleNext = () => {
        setActiveStep((prevActiveStep) => prevActiveStep + 1);
    };

    const handleBack = () => {
        setActiveStep((prevActiveStep) => prevActiveStep - 1);
    };


    const handleReset = () => {
        setActiveStep(0);
        props.storeFI.clearStore();
    };

    return (
        <div className="container-fluid">
            <header className="header-info">
                <table>
                    <tr>
                        <td className="header-info-td">Выбрано изображений:&nbsp;<b>{props.storeFI.selectedImages !== undefined ? props.storeFI.selectedImages.length : 'нет'}</b></td>
                        <td className="header-info-td">
                            <Button
                                variant="contained" color="warning" disabled={activeStep === 0} sx={{ mr: 1, ml: 2 }} onClick={handleReset}>Сброс
                            </Button>
                        </td>
                        <td></td>
                    </tr>
                </table>


            </header>
            <Box className="container-fluid">
                <div sx={{width: '100%'}}>
                    <Stepper nonLinear activeStep={activeStep}>
                        {steps.map((label, index) => {
                            const stepProps = {};
                            const labelProps = {};

                            return (
                                <Step key={label} {...stepProps}>
                                    <StepLabel {...labelProps}>{label}</StepLabel>
                                </Step>
                            );
                        })}
                    </Stepper>
                    <div>
                        {activeStep === steps.length ? (
                            <div>
                                <Button onClick={handleReset}>Reset</Button>
                            </div>
                        ) : (
                            <div>
                                <Typography sx={{ mt: 2, mb: 1 }}>{getStepContent(activeStep)}</Typography>
                                <div className="separator-margin-stepper-btn">
                                    <Button disabled={activeStep === 0} onClick={handleBack} sx={{ mr: 1 }}>
                                        Назад
                                    </Button>


                                    <Button
                                        variant="contained"
                                        color="primary"
                                        disabled={!props.storeFI.step1 || props.storeFI.selectedImages > 0}
                                        hidden={activeStep === steps.length - 1}
                                        onClick={handleNext}
                                        sx={{ mr: 1 }}>
                                        {activeStep === steps.length - 2 ? 'Подготовка к отправке' : 'Далее'}
                                    </Button>
                                </div>
                            </div>
                        )}
                    </div>
                </div>
            </Box>
        </div>
    );
}));

export default ImageStepper;
