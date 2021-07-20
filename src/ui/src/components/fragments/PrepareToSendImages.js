import React from "react";
import {Helmet} from "react-helmet/es/Helmet";
import {inject, observer} from "mobx-react";
import {Field, Form} from "react-final-form";
import storeFI from "../../store/storeFI";

@inject('storeFI')
@observer
class PrepareToSendImages extends React.Component {

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
                    title="Подготовка к отправке..."
                    titleTemplate="Spring web parser - %s" />

                <div className="jumbotron">
                    <h4 className="header-section">Подготовка к отправке</h4>
                    <div className="container-fluid">

                            <span>сюда попробуем сделать превью</span>


                    </div>
                </div>
            </div>
        );
    }
}

export default PrepareToSendImages