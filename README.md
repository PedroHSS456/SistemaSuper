# Chess Auto Player

App Android nativo (Kotlin), **um único APK** autossuficiente: Stockfish 11 embutido (motor clássico, ~1 MB), leitura do tabuleiro, toques automáticos e menu flutuante. Sem Termux, ADB, root, servidor, API ou outro app. Sem permissão de INTERNET (o app não consegue acessar a rede). Desinstalou o APK, acabou.

## Baixar e instalar (só no celular)

1. Na aba **Releases** deste repositório, baixe `Chess-Auto-Player.apk` da versão mais recente.
2. Instale (permita "instalar apps desconhecidos" para o navegador, se pedir).
3. Abra o app, toque em **Permitir sobreposição** e em **Ativar serviço de acessibilidade** (ative "Chess Auto Player").
4. Abra o jogo. O menu flutuante aparece com: **ON/OFF**, **Atraso (ms)** e **Elo**. Arraste pela alça no topo do menu.
5. Para a leitura por imagem funcionar: toque **ON com a posição inicial na tela** e deixe o menu fora do tabuleiro.

O APK é recompilado a cada push na `main` (aba Actions); o workflow compila o Stockfish com o NDK e o empacota.

## Menu

- **ON/OFF**: OFF interrompe a busca do motor e qualquer toque pendente na hora; desligado, nunca joga.
- **Atraso**: 0–5000 ms de espera antes de cada jogada.
- **Elo**: 600–2900. De 1350 a 2849 usa `UCI_Elo` do Stockfish. De 2850 para cima = força máxima. Abaixo de 1350 o Stockfish não desce, então é uma aproximação (escolhe entre os melhores lances com chance crescente de errar). Nenhum valor equivale a um Elo oficial: é uma simulação.

## Limitações reais do Android (não dá para contornar)

- **O AccessibilityService só enxerga o que o app do jogo expõe.** Se o jogo descrever as peças ("white pawn e2"), o app lê direto pela árvore de acessibilidade. Se não descrever, o app tenta ler por imagem.
- **Leitura por imagem exige Android 11+** (`takeScreenshot` do serviço de acessibilidade; a imagem fica só na memória, nunca é gravada). No **Android 10 não existe esse recurso**: nesse caso só funciona se o jogo expuser o tabuleiro.
- A leitura por imagem **aprende o desenho das peças na posição inicial** de cada partida (não há modelo pré-treinado). Ligue o ON no começo da partida. Ela também precisa achar o tabuleiro como uma área quadrada na árvore de acessibilidade; se o jogo não expuser nem isso, o estado mostra "Tabuleiro não encontrado".
- Não foi testado em um aparelho nem no Duolingo: o reconhecimento é a parte com maior chance de precisar de ajuste.
- Roque/en passant são deduzidos da posição (não há histórico da partida); promoção usa o botão da peça escolhida se o jogo o expuser.
- Estado do jogo (motivo de não jogar) aparece na tela inicial do app.

## Compilar localmente

O comando de compilação do Stockfish com o NDK está em `.github/workflows/build-apk.yml`; o binário vai para `app/src/main/jniLibs/arm64-v8a/libstockfish.so` e depois `gradle :app:assembleRelease`. Mais simples: deixe o GitHub Actions fazer isso.
