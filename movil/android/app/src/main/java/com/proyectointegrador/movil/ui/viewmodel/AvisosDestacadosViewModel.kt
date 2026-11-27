package com.proyectointegrador.movil.ui.viewmodel

import com.proyectointegrador.movil.data.repository.CuentaRepository
import com.proyectointegrador.movil.model.AvisoDestacado

/** H71: avisos destacados de Inicio. Se piden cada vez que se abre Inicio; no se guardan. */
class AvisosDestacadosViewModel(repo: CuentaRepository) : CargaViewModel<List<AvisoDestacado>>({ repo.avisosDestacados() })
