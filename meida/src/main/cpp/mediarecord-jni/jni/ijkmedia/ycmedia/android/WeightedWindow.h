#ifndef _WEIGHTED_WINDOW_H_
#define	_WEIGHTED_WINDOW_H_

#include <stdlib.h>

const double dPI = 3.1415926535897932384626433832795029;
const double dTWO_PI = 2 * dPI;
const double dFOUR_PI = 4 * dPI;

class CHanningWindow
{
public:
	CHanningWindow(int wndSize);
	~CHanningWindow();

	bool Process(float* pTimeData, int dataCount);

	float ProcessSample(float pcm, int pos);

	void ClearHanningTable();

	void GetHalfWindowTable(float* pHanning);

private:
	void CreateHanningTable();

	int m_wndSize;
	int m_halfWndSize;
	float* m_pHanning;
};

#endif