import log from 'loglevel';
import React, { Component } from 'react';
import Button from '@material-ui/core/Button';
import { makeStyles } from '@material-ui/core/styles';
import Checkbox from '@material-ui/core/Checkbox';

import DeleteIcon from '@material-ui/icons/Delete';
import FolderIcon from '@material-ui/icons/Folder';
import ArrowUpwardIcon from '@material-ui/icons/ArrowUpward';
import MovieIcon from '@material-ui/icons/Movie';
import SubtitlesIcon from '@material-ui/icons/Subtitles';
import DoneIcon from '@material-ui/icons/Done';
import RefreshIcon from '@material-ui/icons/Refresh';
import CachedIcon from '@material-ui/icons/Cached';

import Table from '@material-ui/core/Table';
import TableBody from '@material-ui/core/TableBody';
import TableCell from '@material-ui/core/TableCell';
import TableContainer from '@material-ui/core/TableContainer';
import TableHead from '@material-ui/core/TableHead';
import TableRow from '@material-ui/core/TableRow';
import Paper from '@material-ui/core/Paper';

import TextField from '@material-ui/core/TextField';
import Dialog from '@material-ui/core/Dialog';
import DialogActions from '@material-ui/core/DialogActions';
import DialogContent from '@material-ui/core/DialogContent';
import DialogContentText from '@material-ui/core/DialogContentText';
import DialogTitle from '@material-ui/core/DialogTitle';

const KILO = 1024;
const MEGA = KILO * 1024;
const GIGA = MEGA * 1024;
const directoryCellStyle = {
  cursor: "pointer"
}
const subtitleRegex = /.*\.srt(.\d+)?$/ig


function sizeStr(sizeBytes) {
  if (sizeBytes < KILO) {
    return sizeBytes + " B"
  } else if (sizeBytes < MEGA) {
    return (sizeBytes / KILO).toFixed(2) + " KB"
  } else if (sizeBytes < GIGA) {
    return (sizeBytes / MEGA).toFixed(2) + " MB"
  }
  return (sizeBytes / GIGA).toFixed(2) + " GB"
}

function isVideo(filename) {
  return filename.toLowerCase().endsWith(".mkv") || filename.toLowerCase().endsWith(".mp4")
}

function isSubtitle(filename) {
  return filename.match(subtitleRegex)
}

function apiUrl(path) {
  return (process.env.NODE_ENV === "development" ? "http://localhost:8080" : "") + path
}

class FileTable extends Component {
  state = {
    files: [],
    currentPath: "",
    selectedFiles: [],
    setSubtitleDisabled: true,
    filesChanged: false,
    error: null,
    dialogOpen: false,
    subtitleUrl: ""
  }

  componentDidMount() {
    this.fetchFiles();
  }

  componentDidUpdate(prevProps, prevState) {
    if (prevState.currentPath !== this.state.currentPath || prevState.filesChanged !== this.state.filesChanged) {
      this.fetchFiles();
    }
  }


  fetchFiles() {
    fetch(apiUrl("/files?path=" + encodeURI(this.state.currentPath)))
      .then(
        (result) => result.json(),
        (error) => this.setState({error: error})
      )
      .then((result) => {
        if (result) {
          log.info("File fetch result size: %d", result.length);
          this.setState({
            files: result,
            filesChanged: false
          });
        }
      });
  }

  rowClicked(file) {
    if (file.type !== "DIRECTORY") {
      return;
    }
    this.setState({currentPath: file.path})
  }

  moveUp() {
    const lastSlash = this.state.currentPath.lastIndexOf('/')
    const newPath = lastSlash < 0 ? "" : this.state.currentPath.substring(0, lastSlash)
    this.setState({
      currentPath: newPath
    })
    this.fetchFiles()
  }

  setFileSelected(file, selected) {
    const newSelecteds = this.state.selectedFiles
    if (selected) {
      this.state.selectedFiles.push(file.path)
    } else {
      const index = this.state.selectedFiles.indexOf(file.path)
      this.state.selectedFiles.splice(index, 1)
    }
    this.setState({selectedFiles: newSelecteds, setSubtitleDisabled: !this.canSetSubtitle(newSelecteds)})
  }

  canSetSubtitle(selectedFiles) {
    if (selectedFiles.length !== 2) {
      return false
    }
    if (isVideo(selectedFiles[0]) && isSubtitle(selectedFiles[1])) {
      return true
    }
    if (isSubtitle(selectedFiles[0]) && isVideo(selectedFiles[1])) {
      return true
    }
    return false
  }

  setAsSubtitle() {
    const movie = this.state.selectedFiles[isVideo(this.state.selectedFiles[0]) ? 0 : 1]
    const subtitle = this.state.selectedFiles[isSubtitle(this.state.selectedFiles[0]) ? 0 : 1]
    const requestOptions = {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: null
    };
    const url = apiUrl("/set-subtitle?subtitle=" + encodeURI(subtitle) + "&movie=" + encodeURI(movie))
    fetch(url, requestOptions).then(response => {this.setState({selectedFiles: [], filesChanged: true})})
  }

  reloadMiniDlna() {
    const requestOptions = {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: null
    };
    const url = apiUrl("/reload-minidlna")
    fetch(url, requestOptions);
  }


  downloadSubtitle() {
    this.setState({dialogOpen:false})
    const url = apiUrl("/download-subtitle?url=" + encodeURI(this.state.subtitleUrl)
        + "&path=" + encodeURI(this.state.currentPath))
    fetch(url)
      .then(response => this.setState({subtitleUrl: null, filesChanged: true}))
      .catch(error => this.setState({error: "Downloading subtitle faield: " + error}))
  }

  deleteFiles() {
    const requestOptions = {
      method: 'DELETE',
      body: null
    };
    var url = "/files?"
    this.state.selectedFiles.forEach(file => url += "path=" + encodeURI(file) + "&");
    fetch(apiUrl(url), requestOptions).then(response => {this.setState({selectedFiles: [], filesChanged: true})})
  }

  render() {
    const classes = makeStyles({
      table: {
        minWidth: 650,
      },
    })

    return (
      <div>
      { this.state.error !== null &&
        <input type="textarea" value={this.state.error.message} />
      }
      <Button color="primary" onClick={() => this.moveUp()}>
        <ArrowUpwardIcon />
      </Button>
      <Button color="primary" disabled={this.state.setSubtitleDisabled} onClick={() => this.setAsSubtitle()}>
        Set As Subtitle
        <DoneIcon />
      </Button>
      <Button color="primary" onClick={() => this.setState({filesChanged: true})}>
        <RefreshIcon />
      </Button>
      <Button color="primary" onClick={this.reloadMiniDlna}>
        Reload MiniDLNA
        <CachedIcon />
      </Button>
      <Button color="primary" onClick={() => this.setState({dialogOpen: true})}>
        Download Subtitle
        <SubtitlesIcon color="primary"/>
      </Button>
      <Button color="primary" disabled={this.state.selectedFiles.length == 0} onClick={() => this.deleteFiles()}>
        <DeleteIcon color="primary"/> 
      </Button>

      <Dialog open={this.state.dialogOpen} aria-labelledby="form-dialog-title">
        <DialogTitle id="form-dialog-title">Download Subtitle</DialogTitle>
        <DialogContent>
          <DialogContentText>
            Enter URL to download subtitle for:
          </DialogContentText>
          <TextField
            autoFocus
            margin="dense"
            id="subtitleRule"
            label="Subtitle URL"
            type="email"
            onChange={event => this.setState({subtitleUrl: event.target.value})}
            fullWidth
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => this.setState({dialogOpen: false})} color="primary">
            Cancel
          </Button>
          <Button onClick={() => this.downloadSubtitle()} color="primary">
            Download
          </Button>
        </DialogActions>
      </Dialog>


      <TableContainer color="primary" component={Paper}>
        <Table className={classes.table} aria-label="simple table">
          <TableHead>
            <TableRow>
              <TableCell>&nbsp;</TableCell>
              <TableCell>Name</TableCell>
              <TableCell>&nbsp;</TableCell>
              <TableCell align="center">Size</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {this.state.files.map((file) => (
              <TableRow key={file.name}>
                <TableCell width="10px">
                  <Checkbox checked={this.state.selectedFiles.indexOf(file.path) >= 0} onChange={(event) => this.setFileSelected(file, event.target.checked)} />
                </TableCell>
                <TableCell width="10px">
                  {file.type === "DIRECTORY" &&
                    <FolderIcon color="primary"/>
                  }
                  {isVideo(file.name) &&
                    <MovieIcon color="secondary"/>
                  }
                  {isSubtitle(file.name) &&
                    <SubtitlesIcon color="primary"/>
                  }
                </TableCell>
                <TableCell style={file.type === "DIRECTORY" ? directoryCellStyle : {}}
                  component="th" scope="row" onClick={() => {this.rowClicked(file)}}>
                  {file.name}
                </TableCell>
                <TableCell align="center">{sizeStr(file.size)}</TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </TableContainer>
      <Button color="primary" disabled={this.state.setSubtitleDisabled} onClick={() => this.setAsSubtitle()}>
        Set As Subtitle
        <DoneIcon />
      </Button>
      </div>
    )
  }

}

export default FileTable