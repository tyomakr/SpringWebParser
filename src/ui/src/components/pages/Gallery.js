import React, {useEffect} from "react";
import {Helmet} from "react-helmet/es/Helmet";
import Pagination from "../Pagination";
import {inject, observer} from "mobx-react";
import storeFI from "../../store/storeFI";

import { withStyles } from '@material-ui/core/styles';
import ImageList from '@material-ui/core/ImageList';
import ImageListItem from '@material-ui/core/ImageListItem'
import ImageListItemBar from '@material-ui/core/ImageListItemBar';
import IconButton from '@material-ui/core/IconButton';
import InfoIcon from '@material-ui/icons/Info';
import Fab from '@material-ui/core/Fab';
import AddIcon from '@material-ui/icons/Add';


const useStyles = theme => ({
    // root: {
    //     display: 'flex',
    //     flexWrap: 'wrap',
    //     justifyContent: 'space-around',
    //     overflow: 'hidden',
    //     backgroundColor: theme.palette.background.paper,
    //     objectFit: 'cover'
    // },
    // ImageList: {
    //     width: '95%',
    //     height: '95%',
    //     objectFit: 'cover'
    // },
    // icon: {
    //     color: 'rgba(255, 255, 255, 0.7)',
    // },
    a: {
        display: 'block',
        height: '100%',
    },
    root: {
        display: "flex",
        flexWrap: "nowrap",
        justifyContent: "space-around",
        overflow: "hidden",
        backgroundColor: theme.palette.background.paper
    },
    imageList: {
        flexWrap: "wrap",
        transform: "translateZ(0)",
        width: "100%"
    },
    title: {
        color: "white"
    },
    titleBar: {
        background:
            "linear-gradient(to top, rgba(0,0,0,0.7) 0%, rgba(0,0,0,0.3) 70%, rgba(0,0,0,0) 100%)"
    }
});

@inject('storeFI')
@observer
class Gallery extends React.Component {

    constructor(props) {
        super(props);

        this.state = {
            items: this.props.storeFI.webImages,
            pageOfItems: [],
        };

        this.onChangePage = this.onChangePage.bind(this);
        this.onHandleSelectImages = this.onHandleSelectImages.bind(this);
    }


    onChangePage(pageOfItems) {
        // update state with new page of items
        this.setState({ pageOfItems: pageOfItems });
    }

    //click button
    onHandleSelectImages(webImage) {
        console.log("clicked " + webImage.directLink)
    }


    render() {
        const { classes } = this.props;
        return (
            <div>
                <Helmet
                    htmlAttributes={{"lang": "ru", "amp": undefined}}
                    title="Галерея и выбор изображений"
                    titleTemplate="Spring web parser - %s" />

                <div className="jumbotron">
                    <h3 className="header-section">Просмотр и выбор изображений</h3>
                    <div className="container-fluid">

                        <div className="text-center">

                            {/*<div className={classes.root}>*/}
                            {/*    <ImageList*/}
                            {/*        sx={{width: 300, height: 300}} cols={5} rowHeight={400} gap={5} component={"image"}>*/}
                            {/*        {this.state.pageOfItems.map((webImage) => (*/}
                            {/*            <ImageListItem key={webImage.id} >*/}
                            {/*                <img src={webImage.directLink} className="img-gal" />*/}
                            {/*                <ImageListItemBar*/}
                            {/*                    actionIcon={*/}
                            {/*                        <IconButton type="button"*/}
                            {/*                                    onClick={()=> this.onHandleSelectImages(webImage)}*/}
                            {/*                                    className={classes.icon}>*/}
                            {/*                            <InfoIcon />*/}
                            {/*                        </IconButton>*/}
                            {/*                    }*/}
                            {/*                />*/}
                            {/*            </ImageListItem>*/}
                            {/*        ))}*/}
                            {/*    </ImageList>*/}
                            {/*</div>*/}
                            <ImageList cols={3} rowHeight={164}>
                                {this.state.pageOfItems.map((webImage) => (
                                    <ImageListItem key={webImage.id}>
                                        <a className={classes.a} href="#">
                                            <img
                                                className={classes.img}
                                                src={webImage.directLink}
                                                alt={webImage.id}
                                            />
                                        </a>
                                    </ImageListItem>
                                ))}
                            </ImageList>


                            {/*<button type="button" onClick={() => console.log(this.state.image)}>OK</button>*/}
                            <Pagination items={this.state.items} onChangePage={this.onChangePage}/>
                        </div>

                    </div>
                </div>
            </div>
        );
    }
}

export default withStyles(useStyles)(Gallery)
