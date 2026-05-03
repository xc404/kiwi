#!/bin/bash
#SBATCH -N 1
#SBATCH -n 1
#SBATCH --cpus-per-task=4
#SBATCH --gres=gpu:1
#SBATCH --job-name=cryoems-${movie_name}-slurm

source ~/.bashrc
source activate cryoems

set -m
