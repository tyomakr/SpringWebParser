import React from "react";
import {Helmet} from "react-helmet/es/Helmet";
import {inject, observer} from "mobx-react";
import {Field, Form} from "react-final-form";
import storeFI from "../../store/storeFI";

@inject('storeFI')
@observer
class FWIRequest extends React.Component {

    constructor(props) {
        super(props);
    }

    //get images from backend request
    onSubmit = async values => {
        const {storeFI} = this.props;
        try {
            await storeFI.getWebImagesFromPages(values.num1, values.num2)
            this.props.storeFI.step1 = true;

        } catch (e) {console.log(e)}
    }


    render() {

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
                                onSubmit={this.onSubmit}
                                initialValues={{ num1: '1', num2: '10' }}
                                render={({ handleSubmit, form, submitting, pristine, values }) => (
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
    }
}

export default FWIRequest