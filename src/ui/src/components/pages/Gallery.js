import React, {useEffect} from "react";
import {Helmet} from "react-helmet/es/Helmet";
import Pagination from "../Pagination";
import {inject, observer} from "mobx-react";
import storeFI from "../../store/storeFI";

import { withStyles } from '@material-ui/core/styles';
import ImageList from '@material-ui/core/ImageList';
import ImageListItem from '@material-ui/core/ImageListItem'
import ImageListItemBar from '@material-ui/core/ImageListItemBar';
import ListSubheader from '@material-ui/core/ListSubheader';
import IconButton from '@material-ui/core/IconButton';
import InfoIcon from '@material-ui/icons/Info';


const useStyles = theme => ({
    root: {
        display: 'flex',
        flexWrap: 'wrap',
        justifyContent: 'space-around',
        overflow: 'hidden',
        backgroundColor: theme.palette.background.paper,
    },
    gridList: {
        width: '800',
        height: 'flex',
    },
    icon: {
        color: 'rgba(255, 255, 255, 0.54)',
    },
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
    onHandleSelectImages() {

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

                            <div className={classes.root}>
                                {/*<ImageList rowHeight={300} className={classes.gridList}>*/}
                                <ImageList sx={{width: 500, height: 500}} cols={5} rowHeight={200}>
                                    {this.state.pageOfItems.map((webImage) => (
                                        <ImageListItem key={webImage.id}>
                                            <img src={webImage.directLink} />
                                            <ImageListItemBar
                                                actionIcon={
                                                    <IconButton aria-label={`info about ${webImage.id}`} className={classes.icon}>
                                                        <InfoIcon />
                                                    </IconButton>
                                                }
                                            />
                                        </ImageListItem>
                                    ))}
                                </ImageList>
                            </div>


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
