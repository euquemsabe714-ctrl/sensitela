# Setas na Tela

App Android offline: mostra uma cruz de setas (↑ ↓ ← →) com botão OK no centro por cima de qualquer app.
As setas rolam a tela, o OK clica no item selecionado (como o Enter) e tudo some sozinho quando fica sem uso.

## Como gerar o app
1. Instale o Android Studio (https://developer.android.com/studio).
2. File > Open > escolha a pasta SetasNaTela. Aguarde o "Gradle sync" (precisa de internet só nessa primeira vez).
3. Celular com "Depuração USB" ligada: clique em Run (▶). Ou: Build > Build APK(s) e instale o .apk gerado.

## Como ativar no celular
1. Abra "Setas na Tela" > "Ativar nas configurações de Acessibilidade".
2. Procure o app na lista e ligue o serviço.
3. Android 13+ com APK instalado fora da loja: se o botão estiver cinza, vá em
   Configurações > Apps > Setas na Tela > menu ⋮ > "Permitir configurações restritas" e tente de novo.

## Baixar e instalar (sem Android Studio)
1. Abra a página **Releases** deste repositório no celular e baixe o arquivo `SetasNaTela.apk`.
2. Toque no arquivo e permita "instalar de fontes desconhecidas" para o navegador, se o Android pedir.
3. Siga a seção "Como ativar no celular" acima.

## Para quem mantém o repositório
O GitHub Actions compila o APK sozinho a cada envio. Para publicar uma versão nova na página Releases,
crie uma tag, por exemplo `v1.0`, e envie: `git tag v1.0 && git push origin v1.0`.
