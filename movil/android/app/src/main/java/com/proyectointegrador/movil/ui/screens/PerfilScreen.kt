package com.proyectointegrador.movil.ui.screens

import androidx.compose.runtime.Composable
import com.proyectointegrador.movil.ui.screens.cuenta.CuentaScreen

@Composable
fun PerfilScreen(abrir:(String)->Unit={}) {CuentaScreen(abrir=abrir)}
