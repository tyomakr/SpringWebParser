import React from "react";
import {Helmet} from "react-helmet/es/Helmet";
import FWIRequestForm from "../fragments/FWIRequestForm";


class FWIRequest extends React.Component {

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
                        <FWIRequestForm />

                    </div>
                </div>
            </div>
        );
    }
}

export default FWIRequest