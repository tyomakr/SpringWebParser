import React from 'react';

export default function Stepper({ steps, activeStep, onStepChange }) {
  return (
    <div className="stepper">
      {steps.map((step, index) => {
        const stepNumber = index + 1;
        const isActive = stepNumber === activeStep;
        const isCompleted = stepNumber < activeStep;
        return (
          <button
            key={step.id}
            type="button"
            className={`step ${isActive ? 'active' : ''} ${isCompleted ? 'done' : ''}`}
            onClick={() => onStepChange(stepNumber)}
          >
            <span className="step-index">{stepNumber}</span>
            <span className="step-label">{step.label}</span>
          </button>
        );
      })}
    </div>
  );
}
