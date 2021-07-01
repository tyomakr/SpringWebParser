import React from "react";
import {Helmet} from "react-helmet/es/Helmet";
import {inject, observer} from "mobx-react";
import {Field, Form} from "react-final-form";
import storeFI from "../../store/storeFI";

import ImagePicker from "react-image-picker";
import "react-image-picker/dist/index.css";

@inject('storeFI')
@observer
class FWIRequest extends React.Component {

    constructor(props) {
        super(props);
        this.state = {webImage: null};
        this.onPick = this.onPick.bind(this);
    }

    sleep = ms => new Promise(resolve => setTimeout(resolve, ms))

    onSubmit = async values => {
        //await this.sleep(300)
        const {storeFI} = this.props;
        try {
            await storeFI.getWebImagesFromPages(values.num1, values.num2)
        } catch (e) {console.log(e)}
    }

    onPick(image) {
        this.setState({ image });
    }

    render() {

        return (
            <div>
                <Helmet
                    htmlAttributes={{"lang": "ru", "amp": undefined}}
                    title="Запрос изображений"
                    titleTemplate="Spring web ru.aikr.inet.comimgparser.parser - %s" />

                <div className="jumbotron">
                    <h3 className="header-section">Запрос изображений для отбора</h3>
                    <div className="container-fluid">
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
                                                    onClick={form.reset} disabled={submitting || pristine }>Сбросить
                                            </button>
                                        </div>
                                        <hr/>
                                    </form>
                                )}
                            />
                        </div>
                        <div>
                            <div>
                                <ImagePicker
                                    images={this.props.storeFI.webImages.map((webImage, i) =>
                                        ({src: webImage.directLink, value: i}))}
                                    onPick={this.onPick}
                                    multiple />

                                <button type="button" onClick={() => console.log(this.state.image)}>
                                    Выбрать
                                </button>

                                {/*{this.props.storeFI.webImages.map(webImage =>*/}
                                {/*    <div><img src={webImage.directLink} width="100px"/> </div>*/}


                            </div>
                        </div>

                    </div>
                </div>
            </div>
        );
    }
}

export default FWIRequest