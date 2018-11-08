#include "WeightedWindow.h"
#include <math.h>
#include <memory.h>

CHanningWindow::CHanningWindow(int wndSize): m_wndSize(wndSize), m_halfWndSize(wndSize / 2), m_pHanning(NULL)
{
}

CHanningWindow::~CHanningWindow()
{
	if (m_pHanning != NULL)
	{
		free(m_pHanning);
	}
}

void CHanningWindow::CreateHanningTable()
{
	m_pHanning = (float*)malloc(m_halfWndSize * sizeof(float));
	for (int i = 0; i < m_halfWndSize; i++)
	{
		m_pHanning[i] = float(0.50 - 0.50 * cos(dTWO_PI * (i + 1) / m_wndSize));
	}
}

bool CHanningWindow::Process(float* pTimeData, int dataCount)
{
	if (m_pHanning == NULL)
	{
		CreateHanningTable();
	}

	if (m_wndSize == dataCount)
	{
		int halfWndSize = m_wndSize / 2;
		for (int i = 0; i < halfWndSize; i++)
		{
			*pTimeData = *pTimeData * m_pHanning[i];
			pTimeData++;
		}

		for (int i = 0; i < halfWndSize; i++)
		{
			*pTimeData = *pTimeData * m_pHanning[halfWndSize - i];
			pTimeData++;
		}

		return true;
	}

	return false;
}

float CHanningWindow::ProcessSample(float pcm, int pos)
{
	if (m_pHanning == NULL)
	{
		CreateHanningTable();
	}
	if (pos < m_halfWndSize)
	{
		return pcm * m_pHanning[pos];
	}
	else
	{
		return pcm * m_pHanning[m_wndSize - pos - 1];	
	}
}

void CHanningWindow::GetHalfWindowTable(float* pHanning)
{
	memcpy(pHanning, m_pHanning, m_wndSize / 2 * sizeof(float));
}