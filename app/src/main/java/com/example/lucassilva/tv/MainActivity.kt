package com.example.lucassilva.tv

import android.app.Activity
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.net.Uri
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import android.text.Editable
import android.text.TextWatcher
import java.util.Locale
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

private data class Canal(
    val nome: String,
    val grupo: String,
    val categoria: String,
    val pais: String,
    val url: String,
    val paginaWeb: Boolean = false,
    val descricao: String = ""
)

class MainActivity : Activity() {

    // CONFIGURAÇÃO IPTV: informe as credenciais atuais da sua assinatura.
    // Por segurança, não deixe usuário/senha publicados em repositórios.
    private val servidorIptv = "http://SEU_SERVIDOR:PORTA"
    private val usuarioIptv = "INSIRA_USUARIO"
    private val senhaIptv = "INSIRA_SENHA"

    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile private var carregamentoEmAndamento = false

    private var libVLC: LibVLC? = null
    private var player: MediaPlayer? = null
    private val canais = mutableListOf<Canal>()
    private val exibidos = mutableListOf<Canal>()

    private lateinit var raiz: LinearLayout
    private lateinit var areaVideo: FrameLayout
    private lateinit var playerView: VLCVideoLayout
    private lateinit var webView: WebView
    private lateinit var painel: LinearLayout
    private lateinit var busca: EditText
    private lateinit var filtroCategoria: Spinner
    private lateinit var filtroPais: Spinner
    private lateinit var lista: ListView
    private lateinit var adapter: ArrayAdapter<Canal>
    private lateinit var botaoTela: Button
    private lateinit var controlesRetrato: LinearLayout
    private lateinit var controlesPaisagem: LinearLayout
    private lateinit var statusLista: TextView

    private var indiceAtual = -1
    private var telaCheia = false
    private var categoriaAtual = "Todas"
    private var paisAtual = "Todos"
    private var termoBusca = ""

    private val fundo = Color.rgb(15, 16, 19)
    private val itemFundo = Color.rgb(34, 36, 42)
    private val branco = Color.WHITE

    private val categorias = listOf(
        "Todas", "TV ao vivo", "Jornalismo", "Entretenimento",
        "Educação", "Esportes", "Música", "Filmes", "Séries",
        "Novelas", "Infantil", "Anime", "Documentários", "Comédia",
        "Reality", "Religioso", "Outros"
    )

    private val paises = listOf(
        "Todos", "Minas Gerais", "Brasil", "Estados Unidos", "Canadá",
        "Dinamarca", "Suécia", "Noruega", "México", "Alemanha",
        "Reino Unido", "Espanha", "França", "Itália", "Argentina",
        "Outros países"
    )

    private val paisPorGrupo = mapOf(
        "United States" to "Estados Unidos",
        "Canada" to "Canadá",
        "Denmark" to "Dinamarca",
        "Sweden" to "Suécia",
        "Norway" to "Noruega",
        "Mexico" to "México",
        "Germany" to "Alemanha",
        "United Kingdom" to "Reino Unido",
        "Spain" to "Espanha",
        "France" to "França",
        "Italy" to "Itália",
        "Argentina" to "Argentina"
    )

    private val categoriaPorGrupo = mapOf(
        "Filmes" to "Filmes", "Movies" to "Filmes",
        "Séries" to "Séries", "Novelas" to "Novelas",
        "Infantil" to "Infantil", "Kids" to "Infantil",
        "Nickelodeon" to "Infantil", "Anime" to "Anime",
        "Esportes" to "Esportes", "Notícias" to "Jornalismo",
        "News" to "Jornalismo", "Música" to "Música",
        "Music" to "Música", "Comédia" to "Comédia",
        "Reality" to "Reality", "Curiosidades" to "Documentários",
        "Natureza" to "Documentários", "Investigação" to "Documentários",
        "Mistérios e Sobrenatural" to "Documentários",
        "Estilo de Vida" to "Entretenimento", "Retrô" to "Entretenimento",
        "MTV" to "Música", "South Park" to "Comédia",
        "Jornada nas Estrelas" to "Séries", "TV Brasileira" to "TV ao vivo",
        "General" to "Entretenimento", "Entertainment" to "Entretenimento",
        "Undefined" to "Outros", "Legislative" to "Jornalismo",
        "Religious" to "Religioso", "Outdoor" to "Entretenimento",
        "Boas Festas" to "Entretenimento"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        indiceAtual = savedInstanceState?.getInt("canal", -1) ?: -1
        telaCheia = savedInstanceState?.getBoolean("fullscreen", false) ?: false
        categoriaAtual = savedInstanceState?.getString("categoria", "Todas") ?: "Todas"
        paisAtual = savedInstanceState?.getString("pais", "Todos") ?: "Todos"
        termoBusca = savedInstanceState?.getString("busca", "") ?: ""

        carregarPlaylistLocal()
        adicionarRedeMinas()
        montarInterface()
        atualizarLista()
        aplicarModoTela()
        carregarPlaylistRemota()
    }

    override fun onStart() {
        super.onStart()
        criarPlayer()
    }

    override fun onStop() {
        liberarPlayerVlc()
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("canal", indiceAtual)
        outState.putBoolean("fullscreen", telaCheia)
        outState.putString("categoria", categoriaAtual)
        outState.putString("pais", paisAtual)
        outState.putString("busca", termoBusca)
        super.onSaveInstanceState(outState)
    }

    private fun carregarPlaylistLocal() {
        try {
            val texto = assets.open("LUCAS_FILMES_TV.m3u")
                .bufferedReader(Charsets.UTF_8).use { it.readText() }
            canais.addAll(lerM3u(texto, fonteOnline = false))
        } catch (e: Exception) {
            // A lista online continua sendo carregada mesmo sem o arquivo local.
        }
    }

    private fun carregarPlaylistRemota() {
        if (carregamentoEmAndamento) return

        if (usuarioIptv.startsWith("INSIRA_") || senhaIptv.startsWith("INSIRA_") || servidorIptv.contains("SEU_SERVIDOR")) {
            statusLista.text = "Configure usuário e senha IPTV no início do MainActivity.kt"
            return
        }

        carregamentoEmAndamento = true
        statusLista.text = "Conectando ao servidor IPTV..."

        Thread {
            var conexao: HttpURLConnection? = null
            try {
                val user = URLEncoder.encode(usuarioIptv, "UTF-8")
                val pass = URLEncoder.encode(senhaIptv, "UTF-8")
                val endpoint = "$servidorIptv/get.php?username=$user&password=$pass&type=m3u_plus&output=mpegts"

                conexao = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 20000
                    readTimeout = 60000
                    instanceFollowRedirects = true
                    useCaches = false
                    setRequestProperty("Accept", "audio/x-mpegurl,application/vnd.apple.mpegurl,*/*")
                    setRequestProperty("User-Agent", "LUCAS-SILVA-TV/2.0")
                }

                val responseCode = conexao.responseCode
                if (responseCode !in 200..299) {
                    throw IllegalStateException("Servidor respondeu HTTP $responseCode")
                }

                val texto = conexao.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                if (!texto.trimStart().startsWith("#EXTM3U", ignoreCase = true)) {
                    val trecho = texto.take(180).replace("\n", " ")
                    throw IllegalStateException("Resposta não é M3U. Verifique login, senha ou validade da assinatura. $trecho")
                }

                val recebidos = lerM3u(texto, fonteOnline = true)
                if (recebidos.isEmpty()) {
                    throw IllegalStateException("A lista M3U chegou vazia ou sem endereços de reprodução")
                }

                mainHandler.post {
                    // Substitui apenas a versão online anterior. Preserva os itens locais e a Rede Minas.
                    canais.removeAll { it.grupo.startsWith("IPTV ONLINE • ") }
                    canais.addAll(recebidos)
                    atualizarLista()
                    statusLista.text = "Lista online atualizada: ${recebidos.size} itens"
                    Toast.makeText(
                        this,
                        "${recebidos.size} itens IPTV carregados",
                        Toast.LENGTH_LONG
                    ).show()
                    carregamentoEmAndamento = false
                }
            } catch (e: Exception) {
                mainHandler.post {
                    statusLista.text = "Falha na lista online: ${e.message ?: "erro de conexão"}"
                    Toast.makeText(
                        this,
                        "A lista online não carregou. Confira internet, credenciais e servidor.",
                        Toast.LENGTH_LONG
                    ).show()
                    carregamentoEmAndamento = false
                }
            } finally {
                conexao?.disconnect()
            }
        }.start()
    }

    private fun lerM3u(texto: String, fonteOnline: Boolean): List<Canal> {
        val resultado = mutableListOf<Canal>()
        var info = ""

        for (raw in texto.lineSequence()) {
            val linha = raw.trim()
            if (linha.startsWith("#EXTINF", ignoreCase = true)) {
                info = linha
                continue
            }

            if (linha.isBlank() || linha.startsWith("#") || info.isBlank()) continue

            // O título é o texto depois da última vírgula da linha EXTINF.
            val nome = info.substringAfterLast(",").trim().ifBlank { "Canal sem nome" }
            val grupoOriginal = Regex("""group-title="([^"]*)"""")
                .find(info)?.groupValues?.getOrNull(1) ?: "Undefined"

            val grupo = if (fonteOnline) "IPTV ONLINE • $grupoOriginal" else grupoOriginal
            val pais = paisPorGrupo[grupoOriginal] ?: when (grupoOriginal) {
                "TV Brasileira" -> "Brasil"
                else -> "Outros países"
            }
            val categoria = categoriaPorGrupo[grupoOriginal] ?: classificarNome(nome, grupoOriginal)

            resultado.add(
                Canal(
                    nome = nome,
                    grupo = grupo,
                    categoria = categoria,
                    pais = pais,
                    url = linha,
                    descricao = "$categoria • $grupoOriginal"
                )
            )
            info = ""
        }
        return resultado
    }

    private fun adicionarRedeMinas() {
        // Página oficial ao vivo da MINASplay; abre dentro do aplicativo via WebView.
        canais.add(
            0,
            Canal(
                nome = "Rede Minas — Ao Vivo",
                grupo = "MINAS GERAIS",
                categoria = "TV ao vivo",
                pais = "Minas Gerais",
                url = "https://minasplay.com/aovivo/",
                paginaWeb = true,
                descricao = "Jornalismo • Cultura • Educação • Entretenimento"
            )
        )
    }

    private fun classificarNome(nome: String, grupo: String): String {
        val t = "$nome $grupo".lowercase(Locale.ROOT)
        return when {
            listOf("jornal", "notícia", "noticia", "news", "breaking").any { t.contains(it) } -> "Jornalismo"
            listOf("documentário", "documentario", "documentary", "natureza", "investigação").any { t.contains(it) } -> "Documentários"
            listOf("filme", "filmes", "movie", "cinema").any { t.contains(it) } -> "Filmes"
            listOf("série", "series", "temporada", "season").any { t.contains(it) } -> "Séries"
            listOf("novela", "telenovela").any { t.contains(it) } -> "Novelas"
            listOf("kids", "infantil", "cartoon", "desenho", "junior").any { t.contains(it) } -> "Infantil"
            listOf("sport", "esporte", "futebol").any { t.contains(it) } -> "Esportes"
            listOf("music", "música", "mtv").any { t.contains(it) } -> "Música"
            listOf("comédia", "comedia", "humor").any { t.contains(it) } -> "Comédia"
            listOf("relig", "fé", "igreja").any { t.contains(it) } -> "Religioso"
            else -> "TV ao vivo"
        }
    }

    private fun montarInterface() {
        raiz = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(fundo)
        }

        areaVideo = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }

        playerView = VLCVideoLayout(this)
        areaVideo.addView(playerView, FrameLayout.LayoutParams(-1, -1))

        webView = WebView(this).apply {
            visibility = View.GONE
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.loadsImagesAutomatically = true
            webChromeClient = WebChromeClient()
            webViewClient = WebViewClient()
        }
        areaVideo.addView(webView, FrameLayout.LayoutParams(-1, -1))

        botaoTela = Button(this).apply {
            text = "□"
            textSize = 22f
            setTextColor(branco)
            setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener { alternarTelaCheia() }
        }
        val bt = FrameLayout.LayoutParams(dp(48), dp(48), Gravity.BOTTOM or Gravity.END)
        bt.setMargins(0, 0, dp(6), dp(6))
        areaVideo.addView(botaoTela, bt)

        controlesPaisagem = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            visibility = View.GONE
            addView(botao("‹") { trocarCanal(-1) })
            addView(botao("›") { trocarCanal(1) })
        }
        val cp = FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
        cp.bottomMargin = dp(8)
        areaVideo.addView(controlesPaisagem, cp)

        controlesRetrato = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(3), dp(4), dp(3))
            setBackgroundColor(fundo)
            addView(botao("◀ Anterior") { trocarCanal(-1) })
            addView(botao("Próximo ▶") { trocarCanal(1) })
        }

        painel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(6), dp(8), dp(4))
            setBackgroundColor(fundo)
        }

        busca = EditText(this).apply {
            hint = "Pesquisar canais e programas"
            setSingleLine(true)
            setTextColor(branco)
            setHintTextColor(Color.LTGRAY)
            setPadding(dp(12), 0, dp(12), 0)
            setBackgroundColor(itemFundo)
            setText(termoBusca)
        }

        statusLista = TextView(this).apply {
            text = "Preparando listas..."
            setTextColor(Color.LTGRAY)
            textSize = 11f
            setPadding(dp(4), dp(3), dp(4), dp(3))
        }

        val botaoAtualizarLista = botao("↻ Atualizar") {
            carregarPlaylistRemota()
        }

        filtroCategoria = criarSpinner(categorias)
        filtroPais = criarSpinner(paises)

        val filtros = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(filtroCategoria, LinearLayout.LayoutParams(0, dp(46), 1f).apply { marginEnd = dp(4) })
            addView(filtroPais, LinearLayout.LayoutParams(0, dp(46), 1f).apply { marginStart = dp(4) })
        }

        lista = ListView(this).apply {
            setBackgroundColor(fundo)
            divider = null
        }

        adapter = object : ArrayAdapter<Canal>(
            this, android.R.layout.simple_list_item_2, android.R.id.text1, exibidos
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val v = super.getView(position, convertView, parent)
                val c = getItem(position)
                v.findViewById<TextView>(android.R.id.text1).apply {
                    text = c?.nome
                    setTextColor(branco)
                    textSize = 15f
                }
                v.findViewById<TextView>(android.R.id.text2).apply {
                    text = c?.descricao?.ifBlank { "${c.categoria} • ${c.pais}" }
                        ?: ""
                    setTextColor(Color.LTGRAY)
                    textSize = 11f
                }
                v.setBackgroundColor(fundo)
                return v
            }
        }
        lista.adapter = adapter
        lista.setOnItemClickListener { _, _, pos, _ -> tocarCanal(exibidos[pos]) }

        painel.addView(busca, LinearLayout.LayoutParams(-1, dp(44)))

        val linhaStatus = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(statusLista, LinearLayout.LayoutParams(0, dp(38), 1f))
            addView(botaoAtualizarLista, LinearLayout.LayoutParams(-2, dp(38)))
        }
        painel.addView(linhaStatus, LinearLayout.LayoutParams(-1, dp(40)))

        painel.addView(filtros, LinearLayout.LayoutParams(-1, dp(48)))
        painel.addView(lista, LinearLayout.LayoutParams(-1, 0, 1f))

        raiz.addView(areaVideo, LinearLayout.LayoutParams(-1, dp(225)))
        raiz.addView(controlesRetrato)
        raiz.addView(painel, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(raiz)

        busca.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                termoBusca = s?.toString().orEmpty()
                atualizarLista()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        filtroCategoria.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {}
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                categoriaAtual = categorias[position]
                atualizarLista()
            }
        }
        filtroPais.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {}
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                paisAtual = paises[position]
                atualizarLista()
            }
        }
    }

    private fun criarSpinner(opcoes: List<String>) = Spinner(this).apply {
        adapter = ArrayAdapter(
            this@MainActivity,
            android.R.layout.simple_spinner_dropdown_item,
            opcoes
        )
    }

    private fun botao(texto: String, acao: () -> Unit) = Button(this).apply {
        text = texto
        textSize = 13f
        setTextColor(branco)
        setBackgroundColor(itemFundo)
        setOnClickListener { acao() }
    }

    private fun atualizarLista() {
        if (!::lista.isInitialized) return
        exibidos.clear()
        exibidos.addAll(canais.filter { c ->
            val catOk = categoriaAtual == "Todas" || c.categoria == categoriaAtual
            val paisOk = when (paisAtual) {
                "Todos" -> true
                "Outros países" -> c.pais == "Outros países"
                else -> c.pais == paisAtual
            }
            val buscaOk = termoBusca.isBlank() ||
                c.nome.contains(termoBusca, true) ||
                c.grupo.contains(termoBusca, true) ||
                c.pais.contains(termoBusca, true) ||
                c.descricao.contains(termoBusca, true)
            catOk && paisOk && buscaOk
        })
        adapter.notifyDataSetChanged()
    }

    private fun criarPlayer() {
        if (player != null) return
        try {
            val args = arrayListOf(
                "--no-drop-late-frames",
                "--no-skip-frames",
                "--network-caching=1500",
                "--live-caching=1500"
            )
            libVLC = LibVLC(this, args)
            player = MediaPlayer(libVLC).also { mp ->
                mp.attachViews(playerView, null, false, false)
                mp.setEventListener { event ->
                    mainHandler.post {
                        if (!::statusLista.isInitialized) return@post
                        when (event.type) {
                            MediaPlayer.Event.Opening -> statusLista.text = "Conectando ao canal..."
                            MediaPlayer.Event.Buffering -> statusLista.text = "Carregando: ${event.buffering.toInt()}%"
                            MediaPlayer.Event.Playing -> statusLista.text = "Transmissão conectada"
                            MediaPlayer.Event.EndReached -> statusLista.text = "Transmissão encerrada"
                            MediaPlayer.Event.EncounteredError -> statusLista.text = "Falha na reprodução. Verifique o link ou servidor."
                        }
                    }
                }
            }
            if (indiceAtual in canais.indices) reproduzir(indiceAtual)
        } catch (e: Exception) {
            statusLista.text = "Erro ao iniciar LibVLC: ${e.message}"
        }
    }

    private fun liberarPlayerVlc() {
        try { player?.stop() } catch (_: Exception) {}
        try { player?.detachViews() } catch (_: Exception) {}
        try { player?.release() } catch (_: Exception) {}
        player = null
        try { libVLC?.release() } catch (_: Exception) {}
        libVLC = null
    }

    private fun tocarCanal(c: Canal) {
        val i = canais.indexOf(c)
        if (i < 0) return
        indiceAtual = i
        reproduzir(i)
    }

    private fun reproduzir(i: Int) {
        if (i !in canais.indices) return
        val c = canais[i]
        if (c.paginaWeb) {
            playerView.visibility = View.GONE
            player?.pause()
            webView.visibility = View.VISIBLE
            webView.loadUrl(c.url)
        } else {
            webView.stopLoading()
            webView.visibility = View.GONE
            playerView.visibility = View.VISIBLE
            val p = player ?: run {
                statusLista.text = "Preparando LibVLC..."
                criarPlayer()
                return
            }
            try {
                statusLista.text = "Abrindo: ${c.nome}"
                p.stop()
                val media = Media(libVLC, Uri.parse(c.url))
                media.setHWDecoderEnabled(true, false)
                media.addOption(":network-caching=1500")
                media.addOption(":http-user-agent=VLC/3.0.20 LUCAS-SILVA-TV")
                p.media = media
                media.release()
                p.play()
            } catch (e: Exception) {
                statusLista.text = "Falha ao abrir canal: ${e.message}"
            }
        }
    }

    private fun trocarCanal(direcao: Int) {
        if (exibidos.isEmpty()) return
        val atual = canais.getOrNull(indiceAtual)
        val posAtual = exibidos.indexOf(atual)
        val proximo = if (posAtual < 0) 0
            else (posAtual + direcao + exibidos.size) % exibidos.size
        tocarCanal(exibidos[proximo])
    }

    private fun alternarTelaCheia() {
        telaCheia = !telaCheia
        requestedOrientation = if (telaCheia)
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        aplicarModoTela()
    }

    private fun aplicarModoTela() {
        if (!::painel.isInitialized) return
        if (telaCheia) {
            painel.visibility = View.GONE
            controlesRetrato.visibility = View.GONE
            controlesPaisagem.visibility = View.VISIBLE
            areaVideo.layoutParams.height = -1
            botaoTela.text = "×"
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        } else {
            painel.visibility = View.VISIBLE
            controlesRetrato.visibility = View.VISIBLE
            controlesPaisagem.visibility = View.GONE
            areaVideo.layoutParams.height = dp(225)
            botaoTela.text = "□"
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
        }
        botaoTela.bringToFront()
        areaVideo.requestLayout()
    }

    private fun dp(v: Int): Int =
        (v * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        if (::webView.isInitialized) {
            webView.stopLoading()
            webView.destroy()
        }
        super.onDestroy()
    }
}
