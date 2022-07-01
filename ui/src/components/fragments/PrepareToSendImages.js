import React from "react";
import {Helmet} from "react-helmet/es/Helmet";
import {inject, observer} from "mobx-react";
import storeFI from "../../store/storeFI";
import {toast} from "react-toastify";

const PrepareToSendImages = inject("storeFI")(observer((props) => {

    const onHandleSendImages = () => {
        if (storeFI.selectedImages.length > 0) {
            storeFI.saveAndPublishSelectedImages(storeFI.selectedImages).then(res =>
                toast.success(
                    res, {
                        position: "bottom-right",
                        autoClose: 15000,
                        hideProgressBar: true,
                        closeOnClick: true,
                        pauseOnHover: true,
                        draggable: true,
                        progress: undefined,
                    })
            )
        }
    }

    return (
        <div>
            <Helmet
                htmlAttributes={{"lang": "ru", "amp": undefined}}
                title="Подготовка к отправке..."
                titleTemplate="Spring web parser - %s" />

            <div className="jumbotron">
                <h4 className="header-section">Подготовка к отправке</h4>

                <div className="container-fluid separator-margin">
                    <button className="btn btn-outline-dark" type="button"
                            onClick={()=> onHandleSendImages()}>ОТПРАВИТЬ</button>
                </div>
            </div>
        </div>
    );
}));

export default PrepareToSendImages