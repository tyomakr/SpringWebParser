import React from "react";
import {inject, observer} from "mobx-react";
import {Form, Field} from "react-final-form";
import storeFI from "../../store/storeFI";
import mainStore from "../../store/mainStore";

@inject('storeFI')
@observer
class FWIRequestForm extends React.Component {

    constructor(props) {
        super(props);
    }


    sleep = ms => new Promise(resolve => setTimeout(resolve, ms))

    onSubmit = async values => {
        await this.sleep(300)
        console.log(JSON.stringify(values, 0, 2))
        const {storeFI} = this.props;
        try {
            await storeFI.getWebImagesFromPages(1, 2)

        } catch (e) {console.log(e)}

    }

    render() {
        const {storeFI} = this.props;

        return (
            <div>
                <Form
                    onSubmit={this.onSubmit}
                    initialValues={{ num1: '1', num2: '15' }}
                    render={({ handleSubmit, form, submitting, pristine, values }) => (
                        <form onSubmit={handleSubmit}>

                            <div className="input-group">
                                <span className="input-group-text">Введите диапазон страниц</span>
                                <Field component={"input"} className="form-control" type="text" name="num1"/>
                                <Field component={"input"} className="form-control" type="text" name="num2"/>
                                <button className="btn btn-outline-primary" type="submit">Отправить запрос</button>
                                <button className="btn btn-outline-dark" type="button"
                                        onClick={form.reset} disabled={submitting || pristine}>Очистить
                                </button>
                            </div>
                            <hr/>
                            <span>test json</span>
                            <pre>{JSON.stringify(values, 0, 2)}</pre>
                        </form>
                    )}
                />
            </div>
        );
    }
}

export default FWIRequestForm;

