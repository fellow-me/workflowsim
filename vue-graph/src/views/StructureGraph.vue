<template>
  <div class="structure-graph">
    <div class="header">
      <div class="controls">
        <div class="file-input">
          <label for="xmlFile">
            <span class="icon">📁</span>
            <span>select dax file</span>
          </label>
          <input
              type="file"
              id="xmlFile"
              accept=".xml"
              @change="handleFileUpload">
          <span class="file-name">{{ fileName }}</span>
        </div>
        <div class="buttons">
          <button @click="applyHierarchicalLayout">hierarchical layout</button>
          <button @click="applyCircleLayout">circle layout</button>
          <button @click="fitGraph">fit graph</button>
          <button @click="zoomIn">zoom in</button>
          <button @click="zoomOut">zoom out</button>
        </div>
      </div>
    </div>
    <div class="chart-container" ref="graphContainer"></div>
  </div>
</template>

<script>
import {
  Graph,
  HierarchicalLayout,
  CircleLayout,
  InternalEvent
} from '@maxgraph/core';

export default {
  name: 'StructureGraph',
  data() {
    return {
      graph: null,
      parent: null,
      fileName: 'No file selected',
      vertices: {}
    };
  },
  mounted() {
    this.$nextTick(() => {
      this.initializeGraph();
    });
  },
  beforeUnmount() {
    if (this.graph) {
      this.graph.destroy();
    }
  },
  methods: {
    initializeGraph() {
      const container = this.$refs.graphContainer;

      // Disable context menu
      InternalEvent.disableContextMenu(container);
      // Create graph
      this.graph = new Graph(container);

      this.graph.setPanning(true); // Enable panning
      this.graph.center(true,true);
      this.parent = this.graph.getDefaultParent();
    },

    handleFileUpload(event) {
      const file = event.target.files[0];
      if (!file) return;


      if (!this.graph) return;
      // Clear existing graph first
      this.clearGraph();

      this.fileName = file.name;

      const reader = new FileReader();
      reader.onload = (e) => {
        const xmlString = e.target.result;
        this.parseXMLAndBuildGraph(xmlString);
        this.applyHierarchicalLayout();
      };
      reader.readAsText(file);
    },

    parseXMLAndBuildGraph(xmlString) {
      const parser = new DOMParser();
      const xmlDoc = parser.parseFromString(xmlString, "text/xml");

      this.graph.batchUpdate(() => {
        // 1. Create vertices for ALL jobs
        const jobs = xmlDoc.getElementsByTagName('job');
        for (let i = 0; i < jobs.length; i++) {
          const jobId = jobs[i].getAttribute('id');
          if (!this.vertices[jobId]) {
            this.vertices[jobId] = this.graph.insertVertex({
              parent: this.parent,
              value: jobId,
              x: 0,
              y: 0,
              width: 100,
              height: 40,
              style: 'fillColor=#ffffff;strokeColor=#333333;rounded=1'
            });
          }
        }

        // 2. Create edges from dependencies
        const childNodes = xmlDoc.getElementsByTagName('child');
        for (let i = 0; i < childNodes.length; i++) {
          const childRef = childNodes[i].getAttribute('ref');
          const parents = childNodes[i].getElementsByTagName('parent');
          for (let j = 0; j < parents.length; j++) {
            const parentRef = parents[j].getAttribute('ref');
            this.graph.insertEdge({
              parent: this.parent,
              source: this.vertices[parentRef],
              target: this.vertices[childRef],
              style: 'strokeColor=#666666;strokeWidth=2'
            });
          }
        }
      });
    },

    clearGraph() {
      if (!this.graph) return;
      this.graph.batchUpdate(() => {
        const cells = this.graph.getChildCells(this.graph.getDefaultParent());
        this.graph.removeCells(cells);
        this.vertices = {};
      });

      this.fileName = 'No file selected';
      document.getElementById('xmlFile').value = "";
    },

    applyCircleLayout() {
      if (!this.graph) return;
      const layout = new CircleLayout(this.graph);
      layout.execute(this.parent);
      this.fitGraph();
    },

    applyHierarchicalLayout() {
      if (!this.graph) return;
      const layout = new HierarchicalLayout(this.graph);
      layout.execute(this.parent);
      this.fitGraph();
    },

    fitGraph() {
      if (this.graph) this.graph.fit();
    },

    zoomIn() {
      if (this.graph) this.graph.zoomIn();
    },

    zoomOut() {
      if (this.graph) this.graph.zoomOut();
    }
  }
};
</script>

<style scoped>
.structure-graph {
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.header {
  margin-bottom: 0.2rem;
}

.header h2 {
  margin: 0 0 1rem 0;
  color: var(--text-color);
  font-size: 1.5rem;
}

.controls {
  display: flex;
  flex-wrap: wrap;
  gap: 1rem;
  align-items: center;
}


.chart-container {
  flex: 1;
  min-height: 0;
  overflow: auto;
}
</style>
