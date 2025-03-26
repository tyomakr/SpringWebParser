import * as React from "react";
import { Helmet } from "react-helmet/es/Helmet";
import { inject, observer } from "mobx-react";
import { Field, Form } from "react-final-form";
import { toast } from "react-toastify";

// Определение типов
type WebImage = {
    id: string;
    directLink: string;
};

type FWIRequestProps = {
    storeFI: {
        getWebImagesFromPages: (num1: string, num2: string) => Promise<void>;
        step1: boolean;
        webImages: WebImage[]; // Добавлено свойство webImages
    };
};

// Кастомный хук для логики компонента
const useFWIRequestLogic = (props: FWIRequestProps) => {
    const onSubmit = async (values: { num1: string; num2: string }) => {
        try {
            await props.storeFI.getWebImagesFromPages(values.num1, values.num2);
            props.storeFI.step1 = true;

            let count = props.storeFI.webImages.length;
            toast.success(`Успешно! Найдено ${count} шт. изображений. Переходите к следующему шагу`, {
                position: "bottom-right",
                autoClose: 5000,
                hideProgressBar: true,
                closeOnClick: true,
                pauseOnHover: true,
                draggable: true,
                progress: undefined,
            });
        } catch (e) {
            console.log(e);
            toast.error('Возникла проблема. Возможно сервис недоступен.', {
                position: "bottom-right",
                autoClose: 5000,
                hideProgressBar: true,
                closeOnClick: true,
                pauseOnHover: true,
                draggable: true,
                progress: undefined,
            });
        }
    };

    return { onSubmit };
};

// Основной компонент
const FWIRequest: React.FC<FWIRequestProps> = inject("mainStore", "storeFI")(observer((props) => {
    const { onSubmit } = useFWIRequestLogic(props);

    return (
        <div>
            <Helmet
                htmlAttributes={{ "lang": "ru", "amp": undefined }}
                title="Запрос изображений"
                titleTemplate="Spring web parser - %s"
            />

            <div className="jumbotron">
                <h4 className="header-section">Запрос изображений для отбора</h4>
                <div className="container-fluid">
                    <div>
                        <Form
                            onSubmit={onSubmit}
                            initialValues={{ num1: '1', num2: '20' }}
                            render={({ handleSubmit, form, submitting, pristine }) => (
                                <form onSubmit={handleSubmit}>
                                    <div className="input-group">
                                        <span className="input-group-text">Введите диапазон страниц</span>
                                        <Field component={"input"} className="form-control" type="text" name="num1" />
                                        <Field component={"input"} className="form-control" type="text" name="num2" />
                                        <button className="btn btn-outline-primary" type="submit">Отправить запрос</button>
                                        <button className="btn btn-outline-dark" type="button"
                                                onClick={() => form.reset()} // Исправленный вызов reset
                                                disabled={submitting || pristine}>
                                            Сбросить
                                        </button>
                                    </div>
                                    <hr />
                                </form>
                            )}
                        />
                    </div>
                </div>
            </div>
        </div>
    );
}));

export default FWIRequest;