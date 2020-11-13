import log from 'loglevel';
import React, {Component} from 'react'
import FileTable from './FileTable'

log.setLevel("info")

class App extends Component {

  render() {
    return (
      <div className="container">
        <FileTable />
      </div>
    )
  }
}

export default App