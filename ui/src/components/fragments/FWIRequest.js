import React from "react";
import {Helmet} from "react-helmet/es/Helmet";
import {inject, observer} from "mobx-react";
import {Field, Form} from "react-final-form";
import storeFI from "../../store/storeFI";
import {toast} from "react-toastify";


const FWIRequest = inject("mainStore", "storeFI")(observer(() => {

    const onSubmit = async values => {
        try {
            await storeFI.getWebImagesFromPages(values.num1, values.num2);
            storeFI.step1 = true;

            let count = storeFI.webImages.length;
            toast.success('Успешно! Найдено ' + count + ' шт. изображений. Переходите к следующему шагу', {
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
            toast.success('Возникла проблема. Возможно сервис недоступен.', {
                position: "bottom-right",
                autoClose: 5000,
                hideProgressBar: true,
                closeOnClick: true,
                pauseOnHover: true,
                draggable: true,
                progress: undefined,
            });
        }
    }

    return (
        <div>
            <Helmet
                htmlAttributes={{"lang": "ru", "amp": undefined}}
                title="Запрос изображений"
                titleTemplate="Spring web parser - %s" />

            <div className="jumbotron">
                <h4 className="header-section">Запрос изображений для отбора</h4>
                <div className="container-fluid">
                    <div>
                        <Form
                            onSubmit={onSubmit}
                            initialValues={{ num1: '1', num2: '20' }}
                            render={({ handleSubmit, form, submitting, pristine}) => (
                                <form onSubmit={handleSubmit}>

                                    <div className="input-group">
                                        <span className="input-group-text">Введите диапазон страниц</span>
                                        <Field component={"input"} className="form-control" type="text" name="num1"/>
                                        <Field component={"input"} className="form-control" type="text" name="num2"/>
                                        <button className="btn btn-outline-primary" type="submit">Отправить запрос</button>
                                        <button className="btn btn-outline-dark" type="button"
                                                onClick={form.reset} disabled={submitting || pristine }>Сбросить
                                        </button>
                                    </div>
                                    <hr/>
                                </form>
                            )}
                        />
                    </div>
                </div>
            </div>
        </div>
    );
}));


export default FWIRequest