import React from 'react';
import { makeStyles } from '@material-ui/core/styles';
import Stepper from '@material-ui/core/Stepper';
import Step from '@material-ui/core/Step';
import StepLabel from '@material-ui/core/StepLabel';
import Button from '@material-ui/core/Button';
import Typography from '@material-ui/core/Typography';
import FWIRequest from "../components/fragments/FWIRequest"
import {inject, observer} from 'mobx-react';

const useStyles = makeStyles((theme) => ({
    root: {
        width: '95%',
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
            return "case1";
        case 2:
            return 'This is the bit I really care about!';
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
                        {/*<Typography className={classes.instructions}>*/}
                        {/*    All steps completed - you&apos;re finished*/}
                        {/*</Typography>*/}
                        <Button onClick={handleReset} className={classes.button}>
                            Reset
                        </Button>
                    </div>
                ) : (
                    <div>
                        <Typography className={classes.instructions}>{getStepContent(activeStep)}</Typography>
                        <div>
                            <Button disabled={activeStep === 0} onClick={handleBack} className={classes.button}>
                                Back
                            </Button>


                            <Button
                                variant="contained"
                                color="primary"
                                disabled={!props.storeFI.step1}
                                onClick={handleNext}
                                className={classes.button}
                            >
                                {activeStep === steps.length - 1 ? 'Finish' : 'Next'}
                            </Button>
                        </div>
                    </div>
                )}
            </div>
        </div>
    );
}));

export default ImageStepper;