// Read-only diagram. Semantic relationships are supplied exclusively by the Java API.
(() => {
  const app = document.querySelector("#graph-app");
  if (!app) return;
  const svg = document.querySelector("#concept-graph");
  const status = document.querySelector("#graph-status");
  const inspector = document.querySelector("#graph-inspector");
  const depth = document.querySelector("#depth");
  const ns = "http://www.w3.org/2000/svg";
  const element = (tag, attrs = {}) => {
    const item = document.createElementNS(ns, tag);
    for (const [key, value] of Object.entries(attrs)) item.setAttribute(key, String(value));
    return item;
  };
  const label = iri => iri.split("/").at(-1).replaceAll("-", " ");
  const show = edge => {
    inspector.replaceChildren();
    const p = document.createElement("p");
    p.textContent = edge
      ? label(edge.subject) + " — " + label(edge.predicate) + " → " + label(edge.object) + " (" + edge.graph + ", " + edge.kind + ")"
      : "Select an edge to inspect its exact predicate, direction and source graph.";
    inspector.append(p);
  };
  const redraw = async () => {
    status.textContent = "Loading recorded relationships…";
    try {
      const response = await fetch("/concepts/" + encodeURIComponent(app.dataset.slug) + "/graph/data?depth=" + depth.value);
      if (!response.ok) throw Error("HTTP " + response.status);
      const graph = await response.json();
      const nodes = graph.nodes.slice(0, 80);
      const nodeSet = new Set(nodes);
      const edges = graph.edges.filter(edge => nodeSet.has(edge.subject) && nodeSet.has(edge.object));
      svg.replaceChildren();
      const defs = element("defs");
      const marker = element("marker", { id: "arrowhead", markerWidth: 7, markerHeight: 7, refX: 6, refY: 3, orient: "auto" });
      marker.append(element("path", { d: "M0 0L6 3L0 6Z", fill: "context-stroke" }));
      defs.append(marker);
      svg.append(defs);
      const positions = new Map();
      const center = { x: 450, y: 320 }, radius = 225;
      nodes.forEach((name, index) => {
        const angle = (2 * Math.PI * index) / Math.max(1, nodes.length) - Math.PI / 2;
        positions.set(name, name === graph.root
          ? center : { x: center.x + radius * Math.cos(angle), y: center.y + radius * Math.sin(angle) });
      });
      for (const edge of edges) {
        const a = positions.get(edge.subject), b = positions.get(edge.object);
        if (!a || !b) continue;
        const line = element("line", { x1: a.x, y1: a.y, x2: b.x, y2: b.y,
          class: "graph-edge graph-" + edge.kind, "marker-end": "url(#arrowhead)", tabindex: 0 });
        line.addEventListener("click", () => show(edge));
        line.addEventListener("focus", () => show(edge));
        svg.append(line);
      }
      for (const name of nodes) {
        const p = positions.get(name);
        const group = element("g", { tabindex: 0, role: "button", "aria-label": label(name) });
        group.append(element("circle", { cx: p.x, cy: p.y, r: name === graph.root ? 13 : 9,
          class: name === graph.root ? "graph-root" : "graph-node" }));
        const title = element("text", { x: p.x + 10, y: p.y - 11, class: "graph-label" });
        title.textContent = label(name).slice(0, 24);
        group.append(title);
        group.addEventListener("click", () => {
          inspector.replaceChildren();
          const link = document.createElement("a");
          link.href = "/concepts/" + encodeURIComponent(name.split("/").at(-1));
          link.textContent = "Open concept: " + label(name);
          inspector.append(link);
        });
        svg.append(group);
      }
      status.textContent = graph.nodes.length + " nodes; " + graph.edges.length + " directed statements"
        + (graph.truncated || graph.nodes.length > nodes.length ? " (display capped for readability)" : "");
      show(null);
    } catch (error) {
      status.textContent = "Unable to load graph: " + error.message;
      svg.replaceChildren();
    }
  };
  depth.addEventListener("change", redraw);
  redraw();
})();
