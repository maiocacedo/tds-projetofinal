import type { ComponentType } from 'react';
import { useRoutes, type RouteObject } from 'react-router-dom';
import { AppShell } from './AppShell';
import { GuestOnly, LandingGuard, RequireAuth, UnknownRoute } from './guards';
import { paths, type AppPath } from './paths';
import { AcessoPage } from './acesso/AcessoPage';
import { OnboardingPage } from './acesso/OnboardingPage';
import { PerfilPage } from './acesso/PerfilPage';
import { AdminUsuariosPage } from './admin/AdminUsuariosPage';
import { BemVindoPage } from './candidato/BemVindoPage';
import { CasaDetalhePage } from './candidato/CasaDetalhePage';
import { CriarCasaPage } from './candidato/CriarCasaPage';
import { ExplorarPage } from './candidato/ExplorarPage';
import { SolicitacoesPage } from './candidato/SolicitacoesPage';
import { CandidatosPage } from './morador/CandidatosPage';
import { CasaPage } from './morador/CasaPage';
import { ConfigurarCasaPage } from './morador/ConfigurarCasaPage';
import { ContasPage } from './morador/ContasPage';
import { DespesaDetalhePage } from './morador/DespesaDetalhePage';
import { InicioPage } from './morador/InicioPage';
import { MercadoPage } from './morador/MercadoPage';
import { MudancaPage } from './morador/MudancaPage';
import { NovaDespesaPage } from './morador/NovaDespesaPage';
import { NovaTarefaPage } from './morador/NovaTarefaPage';
import { SairCasaPage } from './morador/SairCasaPage';
import { TarefasPage } from './morador/TarefasPage';
import { LandingPage } from './publico/LandingPage';
import { SobrePage } from './publico/SobrePage';

const authPages: [AppPath, ComponentType][] = [
  [paths.onboarding, OnboardingPage],
  [paths.perfil, PerfilPage],
  [paths.explorar, ExplorarPage],
  [paths.criarCasa, CriarCasaPage],
  [paths.casaDetalhe, CasaDetalhePage],
  [paths.solicitacoes, SolicitacoesPage],
  [paths.bemVindo, BemVindoPage],
  [paths.inicio, InicioPage],
  [paths.contas, ContasPage],
  [paths.novaDespesa, NovaDespesaPage],
  [paths.despesaDetalhe, DespesaDetalhePage],
  [paths.tarefas, TarefasPage],
  [paths.novaTarefa, NovaTarefaPage],
  [paths.mercado, MercadoPage],
  [paths.casa, CasaPage],
  [paths.candidatos, CandidatosPage],
  [paths.configurarCasa, ConfigurarCasaPage],
  [paths.sairCasa, SairCasaPage],
  [paths.mudanca, MudancaPage],
  [paths.adminUsuarios, AdminUsuariosPage],
];

function createRoutes(): RouteObject[] {
  return [
    {
      path: paths.landing,
      element: (
        <LandingGuard>
          <LandingPage />
        </LandingGuard>
      ),
    },
    { path: paths.sobre, element: <SobrePage /> },
    {
      path: paths.entrar,
      element: (
        <GuestOnly>
          <AcessoPage mode="entrar" />
        </GuestOnly>
      ),
    },
    {
      path: paths.cadastro,
      element: (
        <GuestOnly>
          <AcessoPage mode="cadastro" />
        </GuestOnly>
      ),
    },
    {
      element: <AppShell />,
      children: authPages.map(([path, Page]) => ({
        path,
        element: (
          <RequireAuth route={path}>
            <Page />
          </RequireAuth>
        ),
      })),
    },
    { path: '*', element: <UnknownRoute /> },
  ];
}

/** Tabela única de rotas: usada pelo App (BrowserRouter) e pelos testes (MemoryRouter). */
export function AppRoutes() {
  return useRoutes(createRoutes());
}
