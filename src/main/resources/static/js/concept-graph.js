(() => {
  const app = document.querySelector("#graph-app");
  if (!app) return;

  const svg = document.querySelector("#concept-graph");
  const status = document.querySelector("#graph-status");
  const inspector = document.querySelector("#graph-inspector");
  const depth = document.querySelector("#graph-depth");
  const kind = document.querySelector("#graph-kind");
  const layer = document.querySelector("#graph-layer");
  const domain = document.querySelector("#graph-domain");
  const predicate = document.querySelector("#graph-predicate");
  const width = 900;
  const height = 650;
  const maxNodes = 200;
  const maxEdges = 200;

  const rootSlug = app.dataset.slug;
  const facets = { domains: new Set(), predicates: new Set() };
  let graph;
  let canvas;
  let selection;
  let loading;
  let evidenceRequest;
  let drag;
  let camera = { x: 0, y: 0, scale: 1 };

  function svgElement(tag, attributes = {}) {
    const item = document.createElementNS("http://www.w3.org/2000/svg", tag);
    for (const [key, value] of Object.entries(attributes)) item.setAttribute(key, String(value));
    return item;
  }

  function html(tag, content, className) {
    const item = document.createElement(tag);
    if (content) item.textContent = content;
    if (className) item.className = className;
    return item;
  }

  function iriLabel(iri) {
    return iri.split(/[/#]/).at(-1)?.replaceAll("-", " ") || iri;
  }

  function nodeLabel(iri) {
    return graph.details.get(iri)?.label || iriLabel(iri);
  }

  function edgeKey(edge) {
    return JSON.stringify([edge.subject, edge.predicate, edge.object, edge.graph]);
  }

  function endpoint(slug, suffix) {
    return "/concepts/" + encodeURIComponent(slug) + suffix;
  }

  async function requestJson(url, signal) {
    const response = await fetch(url, { signal, headers: { Accept: "application/json" } });
    if (response.status === 404) throw Error("Concept not found.");
    if (response.status === 503) throw Error("Knowledge service unavailable.");
    if (!response.ok) throw Error("Request failed (HTTP " + response.status + ").");
    return response.json();
  }

  function updateFacet(select, values) {
    const current = select.value;
    while (select.options.length > 1) select.remove(1);
    const sorted = [...values].sort((a, b) => iriLabel(a).localeCompare(iriLabel(b)) || a.localeCompare(b));
    for (const iri of sorted) {
      const option = new Option(iriLabel(iri), iri);
      option.title = iri;
      select.append(option);
    }
    select.value = values.has(current) ? current : "";
  }

  function mergeNeighborhood(next, previous) {
    const nodes = new Set(previous?.nodes ?? []);
    const edges = new Map(previous?.edges ?? []);
    let truncated = next.truncated || previous?.truncated || false;

    for (const node of next.nodes) {
      if (nodes.size >= maxNodes && !nodes.has(node)) {
        truncated = true;
        continue;
      }
      nodes.add(node);
    }
    for (const edge of next.edges) {
      const key = edgeKey(edge);
      if (!nodes.has(edge.subject) || !nodes.has(edge.object) || (edges.size >= maxEdges && !edges.has(key))) {
        truncated = true;
        continue;
      }
      if (!edges.has(key)) edges.set(key, edge);
      facets.predicates.add(edge.predicate);
    }

    const details = new Map(previous?.details ?? []);
    for (const detail of next.nodeDetails ?? []) {
      if (!nodes.has(detail.iri)) continue;
      details.set(detail.iri, detail);
      for (const iri of detail.domains ?? []) facets.domains.add(iri);
    }

    return { root: previous?.root ?? next.root, nodes, edges, details, truncated };
  }

  function filters(hops) {
    const params = new URLSearchParams({ depth: hops, kind: kind.value, graph: layer.value });
    if (domain.value) params.set("domain", domain.value);
    if (predicate.value) params.set("predicate", predicate.value);
    return params.toString();
  }

  function updateExpandButton() {
    const button = inspector.querySelector("[data-expand]");
    if (button) button.disabled = Boolean(loading) || graph.filters !== filters(depth.value);
  }

  async function load(slug, hops, expand = false) {
    loading?.abort();
    const request = new AbortController();
    loading = request;
    status.textContent = expand ? "Expanding neighborhood…" : "Loading recorded relationships…";
    svg.setAttribute("aria-busy", "true");
    updateExpandButton();
    const params = filters(hops);
    try {
      const next = await requestJson(endpoint(slug, "/graph/data?") + params, request.signal);
      if (request.signal.aborted) return;
      const originalFilters = graph?.filters;
      graph = mergeNeighborhood(next, expand ? graph : null);
      graph.filters = expand ? originalFilters : params;
      if (!expand) camera = { x: 0, y: 0, scale: 1 };
      if (!expand || !selection) selection = { node: graph.root };
      updateFacet(domain, facets.domains);
      updateFacet(predicate, facets.predicates);
      render();
      status.textContent = graph.nodes.size + " nodes · " + graph.edges.size + " directed statements"
        + (graph.truncated ? " · Result limited. Narrow filters or reduce depth." : "")
        + (graph.edges.size === 0 ? " · No statements match these filters." : "");
    } catch (error) {
      if (request.signal.aborted) return;
      status.textContent = error.message + (graph ? " Showing the last loaded neighborhood." : "");
    } finally {
      if (loading === request) {
        loading = null;
        svg.removeAttribute("aria-busy");
        updateExpandButton();
      }
    }
  }

  // Place the returned nodes on concentric rings in API order.
  function positions() {
    const result = new Map([[graph.root, { x: width / 2, y: height / 2, radius: 15 }]]);
    const otherNodes = [...graph.nodes].filter(iri => iri !== graph.root);
    const ringCount = Math.min(6, Math.max(1, Math.ceil(Math.sqrt(otherNodes.length / 6))));
    let offset = 0;
    for (let ring = 1; ring <= ringCount && offset < otherNodes.length; ring++) {
      const count = Math.min(otherNodes.length - offset,
        Math.ceil(otherNodes.length * ring / (ringCount * (ringCount + 1) / 2)));
      const ratio = ring / ringCount;
      for (let i = 0; i < count; i++) {
        const angle = 2 * Math.PI * (i / count + (ring % 2) / 20) - Math.PI / 2;
        result.set(otherNodes[offset++], {
          x: width / 2 + 365 * ratio * Math.cos(angle),
          y: height / 2 + 270 * ratio * Math.sin(angle),
          radius: 8,
        });
      }
    }
    return result;
  }

  function edgePath(edge, points, lane) {
    const a = points.get(edge.subject);
    const b = points.get(edge.object);
    if (a === b) {
      const lift = 26 + Math.abs(lane);
      return {
        path: `M ${a.x - 7} ${a.y - 6} C ${a.x - lift} ${a.y - lift * 2}, ${a.x + lift}
          ${a.y - lift * 2}, ${a.x + 7} ${a.y - 8}`,
        labelX: a.x, labelY: a.y - lift * 1.5,
      };
    }
    const distance = Math.max(1, Math.hypot(b.x - a.x, b.y - a.y));
    const unitX = (b.x - a.x) / distance;
    const unitY = (b.y - a.y) / distance;
    const mx = (a.x + b.x) / 2 - unitY * lane;
    const my = (a.y + b.y) / 2 + unitX * lane;
    return {
      path: `M ${a.x + unitX * a.radius} ${a.y + unitY * a.radius} Q ${mx} ${my}
        ${b.x - unitX * (b.radius + 3)} ${b.y - unitY * (b.radius + 3)}`,
      labelX: (a.x + b.x) / 4 + mx / 2, labelY: (a.y + b.y) / 4 + my / 2 - 4,
    };
  }

  function drawEdges(points) {
    const lanes = new Map();
    const edgeItems = [...graph.edges.entries()];
    for (const item of edgeItems) {
      const [, edge] = item;
      const endpoints = JSON.stringify([edge.subject, edge.object].sort());
      if (!lanes.has(endpoints)) lanes.set(endpoints, []);
      lanes.get(endpoints).push(item);
    }

    const drawn = document.createDocumentFragment();
    for (const parallel of lanes.values()) {
      parallel.forEach(([key, edge], index) => {
        const lane = (index - (parallel.length - 1) / 2) * 16 * (edge.subject <= edge.object ? 1 : -1);
        const path = edgePath(edge, points, lane);
        const group = svgElement("g", { class: "graph-statement", "data-edge": key });
        const title = svgElement("title");
        title.textContent = edge.predicate;
        group.append(title, svgElement("path", { d: path.path, class: "graph-edge-hit" }),
          svgElement("path", { d: path.path, class: "graph-edge graph-" + edge.kind, "marker-end": "url(#arrowhead)" }));
        const label = svgElement("text", {
          x: path.labelX, y: path.labelY, class: "graph-edge-label", "text-anchor": "middle",
        });
        label.textContent = iriLabel(edge.predicate);
        group.append(label);
        group.addEventListener("click", () => select({ edge }));
        drawn.append(group);
      });
    }
    canvas.append(drawn);
  }

  function drawNodes(points) {
    const drawn = document.createDocumentFragment();
    const showAllLabels = graph.nodes.size <= 30;
    for (const [iri, position] of points) {
      const group = svgElement("g", { tabindex: 0, role: "button", "aria-label": nodeLabel(iri),
        "data-node": iri, class: "graph-vertex" });
      group.append(svgElement("circle", { cx: position.x, cy: position.y, r: position.radius,
        class: iri === graph.root ? "graph-root" : "graph-node" }));
      const label = svgElement("text", { x: position.x + position.radius + 4, y: position.y - 9,
        class: "graph-label" + (showAllLabels || iri === graph.root ? " graph-label-visible" : "") });
      label.textContent = nodeLabel(iri);
      group.append(label);
      const activate = () => select({ node: iri });
      group.addEventListener("click", activate);
      group.addEventListener("keydown", event => {
        if (event.key === "Enter" || event.key === " ") {
          event.preventDefault();
          activate();
        }
      });
      drawn.append(group);
    }
    canvas.append(drawn);
  }

  function updateCamera() {
    canvas?.setAttribute("transform", `translate(${camera.x} ${camera.y}) scale(${camera.scale})`);
    svg.classList.toggle("graph-zoomed", camera.scale >= 1.45);
  }

  function render() {
    const defs = svgElement("defs");
    const marker = svgElement("marker", { id: "arrowhead", markerWidth: 7, markerHeight: 7,
      refX: 6, refY: 3, orient: "auto", markerUnits: "userSpaceOnUse" });
    marker.append(svgElement("path", { d: "M0 0L6 3L0 6Z", fill: "context-stroke" }));
    defs.append(marker);
    canvas = svgElement("g");
    svg.replaceChildren(defs, canvas);
    const points = positions();
    drawEdges(points);
    drawNodes(points);
    updateCamera();
    select(selection);
  }

  function metadata(list, label, value) {
    if (value) list.append(html("dt", label), html("dd", value));
  }

  function showNode(iri) {
    const detail = graph.details.get(iri);
    inspector.append(html("h2", nodeLabel(iri)), html("p", iri, "iri"));
    if (detail?.domains?.length) {
      const fields = html("dl");
      metadata(fields, "Domains", detail.domains.map(iriLabel).join(", "));
      inspector.append(fields);
    }
    if (detail?.slug) {
      const link = html("a", "Open concept");
      link.href = endpoint(detail.slug, "");
      const button = html("button", "Expand one hop");
      button.type = "button";
      button.dataset.expand = "";
      button.addEventListener("click", async () => {
        await load(detail.slug, "1", true);
        inspector.querySelector("[data-expand]:enabled")?.focus();
      });
      const actions = html("div", "", "graph-node-actions");
      actions.append(link, button);
      inspector.append(actions);
    }

    const statements = [...graph.edges.values()].filter(edge => edge.subject === iri || edge.object === iri);
    if (!statements.length) return;
    const heading = html("h3", "Visible statements");
    const list = html("ul", "", "graph-statement-list");
    for (const edge of statements) {
      const outgoing = edge.subject === iri;
      const adjacent = outgoing ? edge.object : edge.subject;
      const text = (outgoing ? "→ " : "← ") + iriLabel(edge.predicate) + " · " + nodeLabel(adjacent);
      const button = html("button", text);
      button.type = "button";
      button.addEventListener("click", () => {
        select({ edge });
        inspector.querySelector("h2").focus();
      });
      const item = html("li");
      item.append(button);
      list.append(item);
    }
    inspector.append(heading, list);
  }

  function showEdge(edge) {
    const heading = html("h2", iriLabel(edge.predicate));
    heading.tabIndex = -1;
    const fields = html("dl");
    metadata(fields, "Subject", nodeLabel(edge.subject));
    metadata(fields, "Subject IRI", edge.subject);
    metadata(fields, "Predicate IRI", edge.predicate);
    metadata(fields, "Object", nodeLabel(edge.object));
    metadata(fields, "Object IRI", edge.object);
    metadata(fields, "Source graph", edge.graph);
    metadata(fields, "Relationship type", edge.kind);
    const evidence = html("section", "", "graph-evidence");
    evidence.append(html("h3", "Provenance"));
    inspector.append(heading, fields, evidence);
    const subjectSlug = graph.details.get(edge.subject)?.slug;
    if (!subjectSlug) {
      evidence.append(html("p", "Provenance lookup is unavailable for this subject."));
      return;
    }
    const params = new URLSearchParams({ predicate: edge.predicate, object: edge.object });
    displayEvidence(evidence, endpoint(subjectSlug, "/graph/evidence?") + params);
  }

  async function displayEvidence(section, url) {
    const request = new AbortController();
    evidenceRequest = request;
    const message = html("p", "Loading evidence…");
    section.append(message);
    try {
      const items = await requestJson(url, request.signal);
      if (request.signal.aborted) return;
      if (!items.length) {
        message.textContent = "No provenance was recorded for this statement.";
        return;
      }
      message.remove();
      const list = html("ul", "", "graph-evidence-list");
      for (const item of items) {
        const evidence = html("li");
        const fields = html("dl");
        metadata(fields, "Assertion", item.assertionIri);
        metadata(fields, "Source", item.source);
        metadata(fields, "Witness", item.witness);
        metadata(fields, "Identifier", item.identifier);
        evidence.append(fields);
        list.append(evidence);
      }
      section.append(list);
    } catch (error) {
      if (!request.signal.aborted) message.textContent = "Unable to load evidence. " + error.message;
    }
  }

  function select(next) {
    selection = next;
    evidenceRequest?.abort();
    inspector.replaceChildren();
    for (const group of canvas.querySelectorAll("[data-node], [data-edge]")) {
      const active = next.node ? group.dataset.node === next.node : group.dataset.edge === edgeKey(next.edge);
      group.classList.toggle("graph-selected", active);
      if (group.dataset.node) group.setAttribute("aria-pressed", String(active));
    }
    if (next.node) showNode(next.node);
    else showEdge(next.edge);
    updateExpandButton();
  }

  function pointFromEvent(event) {
    const point = new DOMPoint(event.clientX, event.clientY);
    return point.matrixTransform(svg.getScreenCTM().inverse());
  }

  function zoom(factor, point = { x: width / 2, y: height / 2 }) {
    const scale = Math.min(4, Math.max(0.5, camera.scale * factor));
    const ratio = scale / camera.scale;
    camera.x = point.x - (point.x - camera.x) * ratio;
    camera.y = point.y - (point.y - camera.y) * ratio;
    camera.scale = scale;
    updateCamera();
  }

  document.querySelector("#graph-zoom-in").addEventListener("click", () => zoom(1.25));
  document.querySelector("#graph-zoom-out").addEventListener("click", () => zoom(0.8));
  document.querySelector("#graph-reset").addEventListener("click", () => {
    camera = { x: 0, y: 0, scale: 1 };
    updateCamera();
  });
  svg.addEventListener("wheel", event => {
    if (!event.ctrlKey || !canvas) return;
    event.preventDefault();
    zoom(event.deltaY > 0 ? 0.9 : 1.1, pointFromEvent(event));
  }, { passive: false });
  svg.addEventListener("pointerdown", event => {
    if (!canvas || event.button !== 0 || event.target.closest("[data-node], [data-edge]")) return;
    const point = pointFromEvent(event);
    drag = { id: event.pointerId, x: point.x - camera.x, y: point.y - camera.y };
    svg.setPointerCapture(event.pointerId);
    svg.classList.add("graph-dragging");
  });
  svg.addEventListener("pointermove", event => {
    if (drag?.id !== event.pointerId) return;
    const point = pointFromEvent(event);
    camera.x = point.x - drag.x;
    camera.y = point.y - drag.y;
    updateCamera();
  });
  svg.addEventListener("lostpointercapture", () => {
    drag = null;
    svg.classList.remove("graph-dragging");
  });
  svg.addEventListener("keydown", event => {
    if (event.target !== svg || !canvas) return;
    const pan = { ArrowLeft: [35, 0], ArrowRight: [-35, 0], ArrowUp: [0, 35], ArrowDown: [0, -35] };
    if (!Object.hasOwn(pan, event.key)) return;
    event.preventDefault();
    const [x, y] = pan[event.key];
    camera.x += x;
    camera.y += y;
    updateCamera();
  });
  for (const control of [depth, kind, layer, domain, predicate]) {
    control.addEventListener("change", () => load(rootSlug, depth.value));
  }
  load(rootSlug, depth.value);
})();
