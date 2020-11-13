import log from 'loglevel';
import React, { Component } from 'react';
import Button from '@material-ui/core/Button';
import { makeStyles } from '@material-ui/core/styles';
import Checkbox from '@material-ui/core/Checkbox';

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

class FileTable extends Component {
  state = {
    files: [],
    currentPath: "",
    selectedFiles: [],
    setSubtitleDisabled: true,
    filesChanged: false,
    error: null
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
    const url = "/files/" + this.state.currentPath;
    fetch(url)
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
    this.setState({
      currentPath: this.state.currentPath + "/.."
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
    const url = "http://localhost:8080/set-subtitle?subtitle=" + subtitle + "&movie=" + movie
    fetch(url, requestOptions).then(response => {this.setState({selectedFiles: [], filesChanged: true})})
  }

  reloadMiniDlna() {
    const requestOptions = {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: null
    };
    const url = "http://localhost:8080/reload-minidlna"
    fetch(url, requestOptions);
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
                  {file.type === "REGULAR" && (isVideo(file.name) || isSubtitle(file.name)) &&
                      <Checkbox checked={this.state.selectedFiles.indexOf(file.path) >= 0} onChange={(event) => this.setFileSelected(file, event.target.checked)} />
                  }
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