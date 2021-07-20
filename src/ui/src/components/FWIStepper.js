import React from 'react';
import { makeStyles } from '@material-ui/core/styles';
import Stepper from '@material-ui/core/Stepper';
import Step from '@material-ui/core/Step';
import StepLabel from '@material-ui/core/StepLabel';
import Button from '@material-ui/core/Button';
import Typography from '@material-ui/core/Typography';
import FWIRequest from "../components/fragments/FWIRequest"
import {inject, observer} from 'mobx-react';
import Gallery from "./pages/Gallery";
import PrepareToSendImages from "./fragments/PrepareToSendImages";

const useStyles = makeStyles((theme) => ({
    root: {
        width: '95%',
        marginBottom: theme.spacing(10)
    },
    button: {
        marginLeft: theme.spacing(2),
        marginRight: theme.spacing(1),
    },
    instructions: {
        marginTop: theme.spacing(1),
        marginLeft: theme.spacing(2),
        marginBottom: theme.spacing(1),
    },
}));

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
    const classes = useStyles();
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
        <div className={classes.root}>
            <Stepper activeStep={activeStep}>
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
                        <Button onClick={handleReset} className={classes.button}>
                            Reset
                        </Button>
                    </div>
                ) : (
                    <div>
                        <Typography className={classes.instructions}>{getStepContent(activeStep)}</Typography>
                        <div>
                            <Button disabled={activeStep === 0} onClick={handleBack} className={classes.button}>
                                Назад
                            </Button>


                            <Button
                                variant="contained"
                                color="primary"
                                disabled={!props.storeFI.step1 || props.storeFI.selectedImages > 0}
                                hidden={activeStep === steps.length - 1}
                                onClick={handleNext}
                                className={classes.button}>
                                {activeStep === steps.length - 2 ? 'Подготовка к отправке' : 'Далее'}
                            </Button>

                            <Button
                                variant="contained"
                                color="warning"
                                disabled={activeStep === 0}
                                onClick={handleReset}
                                className={classes.button}>
                                Сброс
                            </Button>
                        </div>
                    </div>
                )}
            </div>
            <hr />
            <div className="container-fluid">
                <div>
                    Выбрано изображений:
                    <b>{props.storeFI.selectedImages !== undefined ? props.storeFI.selectedImages.length : 'нет'}</b>
                </div>

            </div>

        </div>
    );
}));

export default ImageStepper;