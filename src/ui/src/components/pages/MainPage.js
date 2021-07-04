import React, {Component} from "react";
import {Helmet} from "react-helmet/es/Helmet";

class MainPage extends Component {

    // eslint-disable-next-line
    constructor(props) {
        super(props);
    }

    render() {
        return (
            <div>
                <Helmet
                    htmlAttributes={{"lang": "ru", "amp": undefined}}
                    title="Главная"
                    titleTemplate="Spring web parser - %s" />

                <div className="jumbotron">
                    <h3 className="header-section">Система парсинга данных</h3>
                    <div className="container-fluid">
                        <span>Тут пока пустая главная страница</span>
                    </div>

                    <div>

                    </div>
                </div>
            </div>
        );
    }
}


export default MainPage;