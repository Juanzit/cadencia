[CmdletBinding()]
param(
    [Parameter(Mandatory = $false)]
    [string]$Data,

    [switch]$Sobrescrever
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$EncodingOriginal = $null
try {
    $EncodingOriginal = [Console]::OutputEncoding
    [Console]::OutputEncoding = New-Object System.Text.UTF8Encoding($false)
} catch {
}

function Restore-ConsoleEncoding {
    if ($null -ne $EncodingOriginal) {
        try {
            [Console]::OutputEncoding = $EncodingOriginal
        } catch {
        }
    }
}

function Sair-Script {
    param([Parameter(Mandatory = $true)][int]$Codigo)
    Restore-ConsoleEncoding
    exit $Codigo
}

function Escrever-Erro {
    param([Parameter(Mandatory = $true)][string]$Mensagem)
    [Console]::Error.WriteLine($Mensagem)
}

function Format-ArgumentoComando {
    param(
        [Parameter(Mandatory = $true)]
        [AllowEmptyString()]
        [string]$Valor
    )

    if ($Valor.Length -eq 0) {
        return '""'
    }
    if ($Valor -notmatch '[\s"]') {
        return $Valor
    }

    $escapado = [regex]::Replace($Valor, '(\\*)"', '$1$1\"')
    $escapado = [regex]::Replace($escapado, '(\\+)$', '$1$1')
    return '"' + $escapado + '"'
}

function Invoke-Cadencia {
    param(
        [Parameter(Mandatory = $true)][string]$ExecutavelJava,
        [Parameter(Mandatory = $true)][string]$DiretorioTrabalho,
        [Parameter(Mandatory = $true)][string[]]$Argumentos
    )

    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = $ExecutavelJava
    $psi.WorkingDirectory = $DiretorioTrabalho
    $psi.UseShellExecute = $false
    $psi.RedirectStandardOutput = $true
    $psi.RedirectStandardError = $true
    $psi.CreateNoWindow = $true
    $psi.StandardOutputEncoding = New-Object System.Text.UTF8Encoding($false)
    $psi.StandardErrorEncoding = New-Object System.Text.UTF8Encoding($false)

    $propArgList = $psi.PSObject.Properties['ArgumentList']
    if ($null -ne $propArgList -and $null -ne $psi.ArgumentList) {
        foreach ($arg in $Argumentos) {
            [void]$psi.ArgumentList.Add($arg)
        }
    } else {
        $formatados = @()
        foreach ($arg in $Argumentos) {
            $formatados += (Format-ArgumentoComando -Valor $arg)
        }
        $psi.Arguments = ($formatados -join ' ')
    }

    $processo = New-Object System.Diagnostics.Process
    $processo.StartInfo = $psi
    [void]$processo.Start()

    $tarefaErr = $processo.StandardError.ReadToEndAsync()
    $stdout = $processo.StandardOutput.ReadToEnd()
    $processo.WaitForExit()
    $stderr = $tarefaErr.GetAwaiter().GetResult()

    return [PSCustomObject]@{
        ExitCode = $processo.ExitCode
        StdOut   = $stdout
        StdErr   = $stderr
    }
}

function Write-SaidaProcesso {
    param([Parameter(Mandatory = $true)][psobject]$Resultado)

    if (-not [string]::IsNullOrWhiteSpace($Resultado.StdOut)) {
        $textoLimpo = $Resultado.StdOut.TrimEnd("`r", "`n")
        foreach ($linha in ($textoLimpo -split "`r?`n")) {
            Write-Output $linha
        }
    }
    if (-not [string]::IsNullOrWhiteSpace($Resultado.StdErr)) {
        [Console]::Error.Write($Resultado.StdErr)
        if (-not $Resultado.StdErr.EndsWith("`n")) {
            [Console]::Error.WriteLine()
        }
    }
}

function Get-SemanaIsoCodigo {
    param(
        [Parameter(Mandatory = $false)][string]$DataTexto,
        [Parameter(Mandatory = $true)][bool]$DataInformada
    )

    if ($DataInformada) {
        $dataRef = [datetime]::ParseExact(
            $DataTexto,
            'yyyy-MM-dd',
            [System.Globalization.CultureInfo]::InvariantCulture
        )
    } else {
        $utcAgora = [datetime]::UtcNow
        $tz = $null
        foreach ($tzId in @('E. South America Standard Time', 'America/Sao_Paulo')) {
            try {
                $tz = [System.TimeZoneInfo]::FindSystemTimeZoneById($tzId)
                break
            } catch {
            }
        }
        if ($null -ne $tz) {
            $dataRef = [System.TimeZoneInfo]::ConvertTimeFromUtc($utcAgora, $tz).Date
        } else {
            $dataRef = [datetime]::Today
        }
    }

    # Regra ISO-8601 compativel com Windows PowerShell 5.1 (.NET Framework sem ISOWeek):
    # a quinta-feira da semana define o ano ISO e o numero da semana (inclusive na virada de ano).
    $diaSemanaIso = (([int]$dataRef.DayOfWeek + 6) % 7) + 1
    $quintaFeira = $dataRef.AddDays(4 - $diaSemanaIso)
    $anoIso = $quintaFeira.Year
    $semanaIso = [int][Math]::Floor(($quintaFeira.DayOfYear - 1) / 7) + 1
    return ('{0:D4}-W{1:D2}' -f $anoIso, $semanaIso)
}

function Test-FeedbackIntocado {
    param(
        [Parameter(Mandatory = $true)][string]$Caminho,
        [Parameter(Mandatory = $true)][string]$SemanaIsoEsperada
    )

    $utf8SemBom = New-Object System.Text.UTF8Encoding($false)
    $linhas = [System.IO.File]::ReadAllLines($Caminho, $utf8SemBom)
    if ($linhas.Length -eq 0) {
        return $false
    }

    $viuCabecalho = $false
    $viuBlocos = $false
    $secao = 'ANTES'
    $padraoItemIntocado = '^(?:-\s+)?\[\s\]\s+(.+)$'
    $padraoCabecalhoItem = '^(?:seg|ter|qua|qui|sex|s[\u00E1a]b|dom)\s+(?:[01]\d|2[0-3]):[0-5]\d(?:\s*[\u2013-]\s*(?:[01]\d|2[0-3]):[0-5]\d)?\s+(?:\u00B7\s+)?[^\s\u00B7].*$'

    for ($i = 0; $i -lt $linhas.Length; $i++) {
        $linha = $linhas[$i]
        if ($i -eq 0 -and $linha.Length -gt 0 -and $linha[0] -eq [char]0xFEFF) {
            $linha = $linha.Substring(1)
        }
        $linha = $linha.Trim()
        if ($linha.Length -eq 0) {
            continue
        }

        if (-not $viuCabecalho) {
            if ($linha -notmatch '^#\s+Feedback\s+(\d{4}-W\d{2})\s*$') {
                return $false
            }
            if ($Matches[1] -cne $SemanaIsoEsperada) {
                return $false
            }
            $viuCabecalho = $true
            continue
        }

        if ($linha.StartsWith('#')) {
            if ($linha -ceq '## Blocos e estudos' -and $secao -eq 'ANTES') {
                $secao = 'BLOCOS'
                $viuBlocos = $true
                continue
            }
            if ($linha -ceq '## Notas livres' -and $secao -ne 'NOTAS') {
                $secao = 'NOTAS'
                continue
            }
            return $false
        }

        if ($secao -eq 'NOTAS') {
            if ($linha -ceq '- Nenhuma nota registrada.') {
                continue
            }
            return $false
        }

        if ($secao -ne 'BLOCOS') {
            return $false
        }

        if ($linha -notmatch $padraoItemIntocado) {
            return $false
        }

        $conteudoItem = $Matches[1]
        $partes = [regex]::Split($conteudoItem, '(?<!\\)\|')
        if ($partes.Count -lt 2 -or $partes.Count -gt 3) {
            return $false
        }
        if ($partes[0].Trim() -notmatch $padraoCabecalhoItem) {
            return $false
        }
        if ($partes[1].Trim() -cne 'NAO_ACONTECEU') {
            return $false
        }
        if ($partes.Count -eq 3 -and -not [string]::IsNullOrWhiteSpace($partes[2])) {
            return $false
        }
    }

    return ($viuCabecalho -and $viuBlocos -and ($secao -eq 'NOTAS'))
}

$DataInformada = $PSBoundParameters.ContainsKey('Data')
if ($DataInformada) {
    if ([string]::IsNullOrWhiteSpace($Data) -or $Data -notmatch '^\d{4}-\d{2}-\d{2}$') {
        Escrever-Erro "Parametro -Data invalido: '$Data'. Informe no formato AAAA-MM-DD (ex.: 2026-11-23)."
        Sair-Script -Codigo 2
    }
    $dataConvertida = [datetime]::MinValue
    $formatoValido = [datetime]::TryParseExact(
        $Data,
        'yyyy-MM-dd',
        [System.Globalization.CultureInfo]::InvariantCulture,
        [System.Globalization.DateTimeStyles]::None,
        [ref]$dataConvertida
    )
    if (-not $formatoValido) {
        Escrever-Erro "Data inexistente no calendario: '$Data'. Informe uma data valida no formato AAAA-MM-DD."
        Sair-Script -Codigo 2
    }
}

$DiretorioScript = $PSScriptRoot
if ([string]::IsNullOrWhiteSpace($DiretorioScript)) {
    $DiretorioScript = Split-Path -Parent $MyInvocation.MyCommand.Path
}
$RaizProjeto = Split-Path -Parent $DiretorioScript
$JarPath = Join-Path (Join-Path $RaizProjeto 'target') 'cadencia-0.1.0-SNAPSHOT.jar'
$DadosDir = Join-Path $RaizProjeto 'dados'
$AgendaPath = Join-Path $DadosDir 'agenda.yaml'
$BacklogPath = Join-Path $DadosDir 'backlog.yaml'
$FeedbackDir = Join-Path $DadosDir 'feedback'
$SemanaDir = Join-Path $DadosDir 'semana'

$ComandoJava = Get-Command 'java' -ErrorAction SilentlyContinue
if ($null -eq $ComandoJava) {
    Escrever-Erro 'Requisito ausente: comando "java" nao encontrado no PATH. Instale o JDK Java 21 e tente novamente.'
    Sair-Script -Codigo 1
}

if (-not (Test-Path -LiteralPath $JarPath -PathType Leaf)) {
    Escrever-Erro "Arquivo obrigatorio ausente: $JarPath. Compile o pacote antes com '.\mvnw.cmd -B clean package' ou '.\mvnw.cmd -B clean verify'."
    Sair-Script -Codigo 1
}
if (-not (Test-Path -LiteralPath $AgendaPath -PathType Leaf)) {
    Escrever-Erro "Arquivo obrigatorio ausente: $AgendaPath. Crie ou copie sua agenda em dados/agenda.yaml antes de executar."
    Sair-Script -Codigo 2
}
if (-not (Test-Path -LiteralPath $BacklogPath -PathType Leaf)) {
    Escrever-Erro "Arquivo obrigatorio ausente: $BacklogPath. Crie ou copie seu backlog em dados/backlog.yaml antes de executar."
    Sair-Script -Codigo 2
}

$ExecutavelJava = if (-not [string]::IsNullOrWhiteSpace($ComandoJava.Source)) {
    $ComandoJava.Source
} else {
    $ComandoJava.Path
}

$ArgsBase = @(
    "-Dcadencia.diretorio-dados=$DadosDir",
    '-Dfile.encoding=UTF-8',
    '-Dstdout.encoding=UTF-8',
    '-Dstderr.encoding=UTF-8',
    '-jar',
    $JarPath
)

$ArgsSemana = @()
$ArgsSemana += $ArgsBase
$ArgsSemana += 'semana'
if ($DataInformada) {
    $ArgsSemana += @('--data', $Data)
}

$ResultadoSemana = Invoke-Cadencia -ExecutavelJava $ExecutavelJava -DiretorioTrabalho $RaizProjeto -Argumentos $ArgsSemana
Write-SaidaProcesso -Resultado $ResultadoSemana
if ($ResultadoSemana.ExitCode -ne 0) {
    Escrever-Erro "Falha ao gerar o plano semanal (codigo $($ResultadoSemana.ExitCode)). O arquivo de feedback nao foi gerado."
    Sair-Script -Codigo $ResultadoSemana.ExitCode
}

$SemanaIso = $null
if ($ResultadoSemana.StdOut -match 'Plano semanal (\d{4}-W\d{2})\b') {
    $SemanaIso = $Matches[1]
} else {
    $SemanaIso = Get-SemanaIsoCodigo -DataTexto $Data -DataInformada:$DataInformada
}

function Invoke-FeedbackModelo {
    param(
        [Parameter(Mandatory = $true)][string]$ExecutavelJava,
        [Parameter(Mandatory = $true)][string]$DiretorioTrabalho,
        [Parameter(Mandatory = $true)][string[]]$ArgsBase,
        [Parameter(Mandatory = $false)][string]$DataTexto,
        [Parameter(Mandatory = $true)][bool]$DataInformada,
        [Parameter(Mandatory = $true)][bool]$ComSobrescrever
    )

    $argsFeedback = @()
    $argsFeedback += $ArgsBase
    $argsFeedback += @('feedback', '--modelo')
    if ($ComSobrescrever) {
        $argsFeedback += '--sobrescrever'
    }
    if ($DataInformada) {
        $argsFeedback += @('--data', $DataTexto)
    }

    $resultado = Invoke-Cadencia -ExecutavelJava $ExecutavelJava -DiretorioTrabalho $DiretorioTrabalho -Argumentos $argsFeedback
    Write-SaidaProcesso -Resultado $resultado
    Sair-Script -Codigo $resultado.ExitCode
}

$PlanoPath = Join-Path $SemanaDir "$SemanaIso.md"
$FeedbackPath = Join-Path $FeedbackDir "$SemanaIso.md"
$FeedbackExiste = Test-Path -LiteralPath $FeedbackPath -PathType Leaf

if (-not $FeedbackExiste) {
    Invoke-FeedbackModelo `
        -ExecutavelJava $ExecutavelJava `
        -DiretorioTrabalho $RaizProjeto `
        -ArgsBase $ArgsBase `
        -DataTexto $Data `
        -DataInformada:$DataInformada `
        -ComSobrescrever:$false
}

$FeedbackIntocado = Test-FeedbackIntocado -Caminho $FeedbackPath -SemanaIsoEsperada $SemanaIso
if ($FeedbackIntocado) {
    if ($Sobrescrever) {
        Write-Output "Aviso: -Sobrescrever informado. O modelo de feedback existente em $FeedbackPath sera substituido."
    } else {
        Write-Output "Feedback existente em $FeedbackPath esta intocado; atualizando o modelo da semana $SemanaIso."
    }

    Invoke-FeedbackModelo `
        -ExecutavelJava $ExecutavelJava `
        -DiretorioTrabalho $RaizProjeto `
        -ArgsBase $ArgsBase `
        -DataTexto $Data `
        -DataInformada:$DataInformada `
        -ComSobrescrever:$true
}

if ($Sobrescrever) {
    Write-Output "Aviso: -Sobrescrever informado. O feedback anterior em $FeedbackPath sera substituido pelo novo modelo."

    Invoke-FeedbackModelo `
        -ExecutavelJava $ExecutavelJava `
        -DiretorioTrabalho $RaizProjeto `
        -ArgsBase $ArgsBase `
        -DataTexto $Data `
        -DataInformada:$DataInformada `
        -ComSobrescrever:$true
}

Write-Output "Plano semanal $SemanaIso atualizado em $PlanoPath, mas o feedback existente em $FeedbackPath foi preservado integralmente (possui preenchimento real ou formato nao reconhecido como modelo intocado)."
Write-Output 'Para recriar o modelo substituindo o feedback anterior, execute novamente informando -Sobrescrever.'
Sair-Script -Codigo 0
