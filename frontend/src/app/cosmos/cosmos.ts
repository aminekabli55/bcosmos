import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  OnInit,
  ViewChild,
  computed,
  inject,
  signal,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import cytoscape, { Core, EventObject } from 'cytoscape';
import { BcosmosApi } from '../core/api/bcosmos-api';
import {
  AnalysisRun,
  GraphEdge,
  GraphNode,
  GraphResponse,
  HotspotItem,
  Repository,
  SymbolImpactResponse,
  formatInstant,
} from '../core/api/models';
import { problemMessage } from '../core/api/problem';

type LayoutType = 'cose' | 'concentric' | 'breadthfirst' | 'circle';

@Component({
  selector: 'app-cosmos',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './cosmos.html',
  styleUrls: ['./cosmos.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Cosmos implements OnInit {
  @ViewChild('cyContainer') private cyContainer?: ElementRef<HTMLDivElement>;

  private readonly api = inject(BcosmosApi);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  private cy?: Core;

  // Repositories & runs
  readonly repositories = signal<Repository[]>([]);
  readonly selectedRepoId = signal<number | null>(null);
  readonly analyses = signal<AnalysisRun[]>([]);
  readonly selectedAnalysisId = signal<number | null>(null);

  // Graph state
  readonly graphData = signal<GraphResponse | null>(null);
  readonly hotspots = signal<HotspotItem[]>([]);
  readonly heuristicFormula = signal<string>('');
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  // Controls & Filters
  readonly activeView = signal<'canvas' | 'table'>('canvas');
  readonly searchQuery = signal<string>('');
  readonly selectedKindFilter = signal<string>('ALL');
  readonly selectedLayout = signal<LayoutType>('cose');
  readonly maxNodes = signal<number>(200);

  // Selection & Impact
  readonly selectedNode = signal<GraphNode | null>(null);
  readonly impactData = signal<SymbolImpactResponse | null>(null);
  readonly impactLoading = signal(false);
  readonly impactError = signal<string | null>(null);

  // Table sorting
  readonly tableSortKey = signal<'score' | 'findingsCount' | 'inDegree' | 'lineCount' | 'name'>('score');
  readonly tableSortDesc = signal(true);

  // Computed lists
  readonly filteredNodes = computed(() => {
    const data = this.graphData();
    if (!data) return [];
    let nodes = data.nodes;
    const query = this.searchQuery().trim().toLowerCase();
    if (query) {
      nodes = nodes.filter(
        (n) =>
          n.name.toLowerCase().includes(query) ||
          n.qualifiedName.toLowerCase().includes(query) ||
          n.filePath.toLowerCase().includes(query) ||
          (n.packageName && n.packageName.toLowerCase().includes(query))
      );
    }
    const kind = this.selectedKindFilter();
    if (kind !== 'ALL') {
      nodes = nodes.filter((n) => n.kind === kind);
    }
    return nodes;
  });

  readonly sortedTableSymbols = computed(() => {
    const nodes = this.filteredNodes();
    const key = this.tableSortKey();
    const desc = this.tableSortDesc();
    return [...nodes].sort((a, b) => {
      let av: number | string = 0;
      let bv: number | string = 0;
      if (key === 'score') {
        av = a.hotspotScore;
        bv = b.hotspotScore;
      } else if (key === 'findingsCount') {
        av = a.findingsCount;
        bv = b.findingsCount;
      } else if (key === 'lineCount') {
        av = a.lineCount;
        bv = b.lineCount;
      } else if (key === 'name') {
        av = a.name.toLowerCase();
        bv = b.name.toLowerCase();
      }
      if (typeof av === 'string') {
        return desc ? (bv as string).localeCompare(av) : av.localeCompare(bv as string);
      }
      return desc ? (bv as number) - (av as number) : (av as number) - (bv as number);
    });
  });

  readonly formatInstant = formatInstant;

  ngOnInit(): void {
    this.loadRepositories();

    this.route.paramMap.subscribe((params) => {
      const idParam = params.get('analysisId');
      if (idParam) {
        const id = Number(idParam);
        if (!isNaN(id)) {
          this.selectedAnalysisId.set(id);
          this.loadAnalysisGraph(id);
        }
      }
    });

    this.destroyRef.onDestroy(() => {
      this.destroyCytoscape();
    });
  }

  loadRepositories(): void {
    this.api.listRepositories().subscribe({
      next: (page) => {
        this.repositories.set(page.items);
        if (page.items.length > 0 && !this.selectedRepoId()) {
          const first = page.items[0];
          this.selectedRepoId.set(first.id);
          this.loadAnalysesForRepo(first.id);
        }
      },
      error: (err: unknown) => {
        this.error.set(problemMessage(err));
      },
    });
  }

  onRepoChange(repoIdStr: string): void {
    const repoId = Number(repoIdStr);
    this.selectedRepoId.set(repoId);
    this.loadAnalysesForRepo(repoId);
  }

  loadAnalysesForRepo(repoId: number): void {
    this.api.listAnalyses(repoId, 0, 30).subscribe({
      next: (page) => {
        const completedRuns = page.items.filter((r) => r.status === 'SUCCEEDED');
        this.analyses.set(completedRuns);
        if (completedRuns.length > 0) {
          const runId = completedRuns[0].id;
          this.selectedAnalysisId.set(runId);
          this.loadAnalysisGraph(runId);
        } else {
          this.selectedAnalysisId.set(null);
          this.graphData.set(null);
          this.hotspots.set([]);
        }
      },
      error: (err: unknown) => {
        this.error.set(problemMessage(err));
      },
    });
  }

  onAnalysisChange(runIdStr: string): void {
    const runId = Number(runIdStr);
    this.selectedAnalysisId.set(runId);
    this.router.navigate(['/cosmos', runId]);
    this.loadAnalysisGraph(runId);
  }

  loadAnalysisGraph(runId: number): void {
    this.loading.set(true);
    this.error.set(null);
    this.selectedNode.set(null);
    this.impactData.set(null);

    // Fetch graph and hotspots in parallel
    this.api.getGraph(runId, this.maxNodes()).subscribe({
      next: (res) => {
        this.graphData.set(res);
        this.loading.set(false);
        // Small delay so ViewChild cyContainer is available in DOM if switching tabs
        setTimeout(() => this.renderCytoscape(), 50);
      },
      error: (err: unknown) => {
        this.loading.set(false);
        this.error.set(problemMessage(err));
      },
    });

    this.api.getHotspots(runId, 20).subscribe({
      next: (res) => {
        this.hotspots.set(res.items);
        this.heuristicFormula.set(res.heuristicFormula);
      },
      error: () => {
        // Non-fatal if hotspots endpoint is unavailable
      },
    });
  }

  private canvasSupported(): boolean {
    try {
      const canvas = document.createElement('canvas');
      return !!canvas && typeof canvas.getContext === 'function' && !!canvas.getContext('2d');
    } catch {
      return false;
    }
  }

  renderCytoscape(): void {
    if (!this.cyContainer?.nativeElement || !this.graphData() || !this.canvasSupported()) {
      return;
    }

    const data = this.graphData()!;
    this.destroyCytoscape();

    const elements: cytoscape.ElementDefinition[] = [];

    // Add nodes
    data.nodes.forEach((node) => {
      // Scaled size according to hotspot score & findings count
      const size = Math.min(32 + Math.sqrt(node.hotspotScore) * 6, 75);
      elements.push({
        group: 'nodes',
        data: {
          id: node.id,
          name: node.name,
          qualifiedName: node.qualifiedName,
          kind: node.kind,
          findingsCount: node.findingsCount,
          hotspotScore: node.hotspotScore,
          isExternal: node.isExternal,
          packageName: node.packageName || 'default',
          size: size,
          color: this.getNodeColor(node),
        },
      });
    });

    // Add edges
    data.edges.forEach((edge) => {
      elements.push({
        group: 'edges',
        data: {
          id: edge.id,
          source: edge.source,
          target: edge.target,
          type: edge.type,
          status: edge.status,
          edgeColor: this.getEdgeColor(edge),
          lineStyle: edge.status === 'UNRESOLVED' ? 'dashed' : 'solid',
        },
      });
    });

    this.cy = cytoscape({
      container: this.cyContainer.nativeElement,
      elements: elements,
      style: [
        {
          selector: 'node',
          style: {
            label: 'data(name)',
            width: 'data(size)',
            height: 'data(size)',
            'background-color': 'data(color)',
            color: '#e2e8f0',
            'font-family': 'Inter, sans-serif',
            'font-size': '11px',
            'font-weight': 600,
            'text-valign': 'bottom',
            'text-margin-y': 6,
            'text-outline-color': '#0f172a',
            'text-outline-width': 2,
            'border-width': 1.5,
            'border-color': '#334155',
            'transition-property': 'background-color, border-color, border-width, width, height',
            'transition-duration': 0.2,
          },
        },
        {
          selector: 'node:selected',
          style: {
            'border-color': '#38bdf8',
            'border-width': 4,
            'border-opacity': 1,
            'background-color': '#0284c7',
          },
        },
        {
          selector: 'node.highlighted',
          style: {
            'border-color': '#f43f5e',
            'border-width': 5,
            'border-opacity': 1,
          },
        },
        {
          selector: 'node.dimmed',
          style: {
            opacity: 0.25,
          },
        },
        {
          selector: 'edge',
          style: {
            width: 1.5,
            'line-color': 'data(edgeColor)',
            'target-arrow-color': 'data(edgeColor)',
            'target-arrow-shape': 'triangle',
            'curve-style': 'bezier',
            'line-style': 'data(lineStyle)' as any,
            'arrow-scale': 0.8,
            opacity: 0.65,
          },
        },
        {
          selector: 'edge.highlighted',
          style: {
            width: 3.5,
            opacity: 1,
            'line-color': '#f43f5e',
            'target-arrow-color': '#f43f5e',
          },
        },
        {
          selector: 'edge.dimmed',
          style: {
            opacity: 0.1,
          },
        },
      ],
      layout: this.getLayoutOptions(this.selectedLayout()),
    });

    this.cy.on('tap', 'node', (evt: EventObject) => {
      const nodeData = evt.target.data();
      const match = data.nodes.find((n) => n.id === nodeData.id);
      if (match) {
        this.selectNode(match);
      }
    });

    this.cy.on('tap', (evt: EventObject) => {
      if (evt.target === this.cy) {
        this.clearSelection();
      }
    });
  }

  private getNodeColor(node: GraphNode): string {
    if (node.isExternal) return '#64748b'; // Slate
    switch (node.kind) {
      case 'INTERFACE':
        return '#14b8a6'; // Teal
      case 'RECORD':
        return '#06b6d4'; // Cyan
      case 'ENUM':
        return '#f59e0b'; // Amber
      case 'METHOD':
        return '#8b5cf6'; // Purple
      default:
        return '#6366f1'; // Indigo for CLASS
    }
  }

  private getEdgeColor(edge: GraphEdge): string {
    if (edge.status === 'UNRESOLVED') return '#f59e0b'; // Amber dotted
    switch (edge.type) {
      case 'EXTENDS':
        return '#a855f7'; // Purple
      case 'IMPLEMENTS':
        return '#10b981'; // Emerald
      case 'CALLS':
        return '#3b82f6'; // Blue
      default:
        return '#94a3b8'; // Slate
    }
  }

  private getLayoutOptions(type: LayoutType): cytoscape.LayoutOptions {
    switch (type) {
      case 'concentric':
        return {
          name: 'concentric',
          concentric: (node: any) => node.data('hotspotScore') || 1,
          levelWidth: () => 10,
          padding: 30,
        };
      case 'breadthfirst':
        return { name: 'breadthfirst', directed: true, padding: 30, spacingFactor: 1.25 };
      case 'circle':
        return { name: 'circle', padding: 30 };
      default:
        return {
          name: 'cose',
          animate: false,
          randomize: false,
          nodeRepulsion: () => 450000,
          idealEdgeLength: () => 70,
          edgeElasticity: () => 100,
          gravity: 0.25,
          padding: 40,
        };
    }
  }

  changeLayout(layout: LayoutType): void {
    this.selectedLayout.set(layout);
    if (this.cy) {
      this.cy.layout(this.getLayoutOptions(layout)).run();
    }
  }

  zoomIn(): void {
    if (this.cy) this.cy.zoom(this.cy.zoom() * 1.3);
  }

  zoomOut(): void {
    if (this.cy) this.cy.zoom(this.cy.zoom() * 0.77);
  }

  fitView(): void {
    if (this.cy) this.cy.fit(undefined, 40);
  }

  selectNode(node: GraphNode): void {
    this.selectedNode.set(node);
    this.impactData.set(null);
    this.impactError.set(null);

    if (this.cy) {
      this.cy.elements().removeClass('highlighted dimmed');
      const cyNode = this.cy.getElementById(node.id);
      if (cyNode && cyNode.length > 0) {
        cyNode.select();
        // Highlight 1-hop neighborhood
        const neighborhood = cyNode.neighborhood().add(cyNode);
        this.cy.elements().not(neighborhood).addClass('dimmed');
        neighborhood.removeClass('dimmed');
      }
    }
  }

  clearSelection(): void {
    this.selectedNode.set(null);
    this.impactData.set(null);
    if (this.cy) {
      this.cy.elements().removeClass('highlighted dimmed');
    }
  }

  focusHotspot(item: HotspotItem): void {
    const data = this.graphData();
    if (!data) return;
    const node = data.nodes.find((n) => n.id === String(item.symbolId) || n.qualifiedName === item.qualifiedName);
    if (node) {
      this.selectNode(node);
      if (this.cy) {
        const cyNode = this.cy.getElementById(node.id);
        if (cyNode.length > 0) {
          this.cy.animate({
            center: { eles: cyNode },
            zoom: 1.4,
            duration: 400,
          });
        }
      }
    }
  }

  runImpactAnalysis(symbolIdStr: string): void {
    const symbolId = Number(symbolIdStr);
    if (isNaN(symbolId) || symbolId <= 0) return;

    this.impactLoading.set(true);
    this.impactError.set(null);

    this.api.getImpact(symbolId, 3).subscribe({
      next: (impact) => {
        this.impactData.set(impact);
        this.impactLoading.set(false);
        this.highlightBlastRadius(impact);
      },
      error: (err: unknown) => {
        this.impactLoading.set(false);
        this.impactError.set(problemMessage(err));
      },
    });
  }

  highlightBlastRadius(impact: SymbolImpactResponse): void {
    if (!this.cy) return;

    this.cy.elements().removeClass('highlighted dimmed');

    const affectedIds = new Set<string>();
    affectedIds.add(String(impact.symbolId));
    impact.directDependents.forEach((d) => affectedIds.add(d.id));
    impact.transitiveDependents.forEach((d) => affectedIds.add(d.id));

    this.cy.nodes().forEach((n) => {
      if (affectedIds.has(n.id())) {
        n.addClass('highlighted');
        n.removeClass('dimmed');
      } else {
        n.addClass('dimmed');
      }
    });

    this.cy.edges().forEach((e) => {
      if (affectedIds.has(e.source().id()) && affectedIds.has(e.target().id())) {
        e.addClass('highlighted');
        e.removeClass('dimmed');
      } else {
        e.addClass('dimmed');
      }
    });
  }

  onSearchChange(query: string): void {
    this.searchQuery.set(query);
    if (!this.cy) return;

    if (!query.trim()) {
      this.cy.elements().removeClass('dimmed highlighted');
      return;
    }

    const q = query.trim().toLowerCase();
    this.cy.nodes().forEach((n) => {
      const name = (n.data('name') || '').toLowerCase();
      const qname = (n.data('qualifiedName') || '').toLowerCase();
      if (name.includes(q) || qname.includes(q)) {
        n.removeClass('dimmed').addClass('highlighted');
      } else {
        n.addClass('dimmed').removeClass('highlighted');
      }
    });
  }

  toggleTableSort(key: 'score' | 'findingsCount' | 'inDegree' | 'lineCount' | 'name'): void {
    if (this.tableSortKey() === key) {
      this.tableSortDesc.set(!this.tableSortDesc());
    } else {
      this.tableSortKey.set(key);
      this.tableSortDesc.set(true);
    }
  }

  setActiveView(view: 'canvas' | 'table'): void {
    this.activeView.set(view);
    if (view === 'canvas') {
      setTimeout(() => {
        if (!this.cy) {
          this.renderCytoscape();
        } else {
          this.cy.resize();
          this.cy.fit(undefined, 40);
        }
      }, 50);
    }
  }

  private destroyCytoscape(): void {
    if (this.cy) {
      this.cy.destroy();
      this.cy = undefined;
    }
  }
}
