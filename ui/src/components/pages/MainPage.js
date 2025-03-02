import React from "react";
import {Helmet} from "react-helmet/es/Helmet";
import {inject, observer} from "mobx-react";


const MainPage = inject("mainStore", "storeFI")(observer((props) => {
    return (
        <div>
            <Helmet
                htmlAttributes={{"lang": "ru", "amp": undefined}}
                title="Главная"
                titleTemplate="Spring web parser - %s"/>

            <div className="jumbotron m-0"> {/* Убираем внешние отступы */}
                <h3 className="header-section">Система парсинга данных</h3>
                <div className="container-fluid p-4"> {/* Добавляем внутренние отступы */}
                    <span>Тут пока пустая главная страница</span>
                </div>
            </div>
        </div>
    );
}));

export default MainPage;